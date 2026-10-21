package com.senzaproject.screenrecorder

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

/**
 * Video (H.264 dari layar) + audio AAC (suara perangkat dan/atau mikrofon, bisa dicampur)
 * di-mux ke satu file MP4.
 */
class ScreenEncoder(
    private val w: Int,
    private val h: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val fd: FileDescriptor,
    private val projection: MediaProjection,
    private val deviceAudio: Boolean,
    private val micAudio: Boolean
) {
    companion object {
        private const val SR = 44100
        private const val FRAMES = 1024
    }

    private val hasAudio = deviceAudio || micAudio
    private lateinit var muxer: MediaMuxer
    private lateinit var vEnc: MediaCodec
    private var aEnc: MediaCodec? = null
    private var devRec: AudioRecord? = null
    private var micRec: AudioRecord? = null
    lateinit var surface: Surface

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var vTrack = -1
    private var aTrack = -1
    private var started = false
    private var aborted = false
    @Volatile private var running = false
    @Volatile var wrote = false
    @Volatile var error: String? = null

    private var baseUs = 0L
    private var firstVideoPts = -1L
    private var firstVideoOffset = 0L
    private var lastVideoPts = 0L
    private var lastAudioPts = 0L
    private var vThread: Thread? = null
    private var aThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun prepare() {
        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
        }
        vEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        vEnc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = vEnc.createInputSurface()

        if (hasAudio) {
            if (deviceAudio) {
                val cfg = AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
                val min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                devRec = AudioRecord.Builder()
                    .setAudioFormat(fmt)
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setBufferSizeInBytes(max(min, FRAMES * 4 * 4))
                    .build()
                if (devRec!!.state != AudioRecord.STATE_INITIALIZED)
                    throw IllegalStateException("Suara perangkat tidak bisa diinisialisasi")
            }
            if (micAudio) {
                val min = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                micRec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SR, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(min, FRAMES * 2 * 4)
                )
                if (micRec!!.state != AudioRecord.STATE_INITIALIZED)
                    throw IllegalStateException("Mikrofon tidak bisa diinisialisasi")
            }
            val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SR, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            aEnc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                it.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        }
        muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun start() {
        baseUs = System.nanoTime() / 1000
        running = true
        vEnc.start()
        aEnc?.start()
        vThread = Thread({ videoLoop() }, "enc-video").also { it.start() }
        if (hasAudio) aThread = Thread({ audioLoop() }, "enc-audio").also { it.start() }
    }

    fun stop() {
        running = false
        runCatching { vThread?.join(5000) }
        lock.withLock {
            if (!started) { aborted = true; cond.signalAll() }
        }
        runCatching { aThread?.join(5000) }
        runCatching { vEnc.stop() }; runCatching { vEnc.release() }
        aEnc?.let { runCatching { it.stop() }; runCatching { it.release() } }
        devRec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        micRec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        runCatching { if (started) muxer.stop() }
        runCatching { muxer.release() }
        runCatching { surface.release() }
    }

    // ---------- video ----------
    private fun videoLoop() {
        try {
            var signaled = false
            var eos = false
            while (!eos) {
                if (!running && !signaled) { vEnc.signalEndOfInputStream(); signaled = true }
                eos = drain(vEnc, true, 10_000)
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    // ---------- audio ----------
    private fun audioLoop() {
        try {
            val enc = aEnc!!
            devRec?.startRecording()
            micRec?.startRecording()
            val dev = ShortArray(FRAMES * 2)
            val mic = ShortArray(FRAMES)
            val mixed = ShortArray(FRAMES * 2)
            var total = 0L
            var startUs = -1L
            while (running) {
                if (devRec != null && !readFully(devRec!!, dev)) break
                if (micRec != null && !readFully(micRec!!, mic)) break
                if (startUs < 0) {
                    startUs = max(0L, System.nanoTime() / 1000 - baseUs - FRAMES * 1_000_000L / SR)
                }
                for (i in 0 until FRAMES) {
                    var l = 0
                    var r = 0
                    if (devRec != null) { l += dev[2 * i]; r += dev[2 * i + 1] }
                    if (micRec != null) { l += mic[i]; r += mic[i] }
                    mixed[2 * i] = l.coerceIn(-32768, 32767).toShort()
                    mixed[2 * i + 1] = r.coerceIn(-32768, 32767).toShort()
                }
                drain(enc, false, 0)
                queueAudio(enc, mixed, startUs + total * 1_000_000L / SR, false)
                total += FRAMES
            }
            val endPts = max(0L, startUs) + total * 1_000_000L / SR
            queueAudio(enc, null, endPts, true)
            while (!drain(enc, false, 10_000)) { /* tunggu EOS */ }
        } catch (e: Exception) {
            error = e.message
        }
    }

    private fun readFully(rec: AudioRecord, arr: ShortArray): Boolean {
        var off = 0
        while (off < arr.size) {
            if (!running) return false
            val n = rec.read(arr, off, arr.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun queueAudio(enc: MediaCodec, data: ShortArray?, pts: Long, eos: Boolean) {
        var idx = enc.dequeueInputBuffer(10_000)
        var tries = 0
        while (idx < 0 && tries++ < 300) {
            drain(enc, false, 0)
            idx = enc.dequeueInputBuffer(10_000)
        }
        if (idx < 0) return
        val buf = enc.getInputBuffer(idx)!!
        buf.clear()
        var bytes = 0
        if (data != null) {
            buf.order(ByteOrder.LITTLE_ENDIAN)
            buf.asShortBuffer().put(data, 0, data.size)
            bytes = data.size * 2
        }
        enc.queueInputBuffer(idx, 0, bytes, pts, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
    }

    // ---------- common ----------
    /** @return true jika EOS tercapai */
    private fun drain(enc: MediaCodec, isVideo: Boolean, timeoutUs: Long): Boolean {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val i = enc.dequeueOutputBuffer(info, timeoutUs)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> addTrack(isVideo, enc.outputFormat)
                i >= 0 -> {
                    val buf = enc.getOutputBuffer(i)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        adjustPts(isVideo, info)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        write(isVideo, buf, info)
                    }
                    enc.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    private fun adjustPts(isVideo: Boolean, info: MediaCodec.BufferInfo) {
        if (isVideo) {
            if (firstVideoPts < 0) {
                firstVideoPts = info.presentationTimeUs
                firstVideoOffset = max(0L, System.nanoTime() / 1000 - baseUs)
            }
            var p = info.presentationTimeUs - firstVideoPts + firstVideoOffset
            if (p < lastVideoPts) p = lastVideoPts
            lastVideoPts = p
            info.presentationTimeUs = p
        } else {
            var p = info.presentationTimeUs
            if (p < lastAudioPts) p = lastAudioPts
            lastAudioPts = p
            info.presentationTimeUs = p
        }
    }

    private fun addTrack(isVideo: Boolean, fmt: MediaFormat) {
        lock.withLock {
            if (isVideo) vTrack = muxer.addTrack(fmt) else aTrack = muxer.addTrack(fmt)
            if (vTrack >= 0 && (!hasAudio || aTrack >= 0)) {
                muxer.start()
                started = true
                cond.signalAll()
            }
        }
    }

    private fun write(isVideo: Boolean, buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        lock.withLock {
            while (!started && !aborted) cond.await(100, TimeUnit.MILLISECONDS)
            if (aborted) return
            muxer.writeSampleData(if (isVideo) vTrack else aTrack, buf, info)
            wrote = true
        }
    }
}
