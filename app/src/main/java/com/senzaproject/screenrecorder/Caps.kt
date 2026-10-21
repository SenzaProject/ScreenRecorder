package com.senzaproject.screenrecorder

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Deteksi kemampuan perangkat untuk perekaman layar. */
object Caps {
    data class Res(val label: String, val shortSide: Int) {
        val is2K get() = shortSide == 1440
    }

    private val base = listOf(Res("480p", 480), Res("720p", 720), Res("1080p", 1080))
    private val k2 = Res("2K", 1440)

    val FPS_ALL = listOf(30, 60, 90, 120)

    fun fpsFor(res: Res): List<Int> = if (res.is2K) listOf(120) else FPS_ALL

    /** 480p-1080p selalu terbuka. 2K hanya jika encoder mendukung 2K @ 120fps. */
    fun available(ctx: Context): List<Res> {
        val (w, h) = dims(ctx, k2.shortSide)
        return if (supports(w, h, 120)) base + k2 else base
    }

    fun supports2K(ctx: Context): Boolean = available(ctx).any { it.is2K }

    @Suppress("DEPRECATION")
    fun screenSize(ctx: Context): Pair<Int, Int> {
        val wm = ctx.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    /** Ukuran video mengikuti rasio layar & orientasi saat ini. */
    fun dims(ctx: Context, shortSide: Int): Pair<Int, Int> {
        val (sw, sh) = screenSize(ctx)
        val sMin = min(sw, sh)
        val sMax = max(sw, sh)
        val (wa, ha) = alignment()
        val shortA = if (sw <= sh) wa else ha
        val longA = if (sw <= sh) ha else wa
        val s = roundTo(shortSide, shortA)
        val l = roundTo((shortSide.toDouble() * sMax / sMin).roundToInt(), longA)
        return if (sw <= sh) s to l else l to s
    }

    /** Alignment ukuran dari encoder AVC perangkat (biasanya 2). Alignment kecil = tidak ada skala/letterbox = lebih tajam. */
    private fun alignment(): Pair<Int, Int> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        for (info in list) {
            if (!info.isEncoder || info.supportedTypes.none { it.equals("video/avc", true) }) continue
            val vc = runCatching { info.getCapabilitiesForType("video/avc").videoCapabilities }.getOrNull() ?: continue
            return vc.widthAlignment.coerceAtLeast(2) to vc.heightAlignment.coerceAtLeast(2)
        }
        return 2 to 2
    }

    private fun roundTo(v: Int, a: Int) = max(a, (v.toDouble() / a).roundToInt() * a)

    /** Bitrate berdasarkan jumlah piksel & fps. Lebih tinggi = lebih jernih. */
    fun bitrateFor(w: Int, h: Int, fps: Int): Int {
        val bpp = when {
            fps >= 90 -> 0.14
            fps >= 60 -> 0.16
            else -> 0.20
        }
        return (w.toLong() * h * fps * bpp).toLong().coerceIn(3_000_000L, 100_000_000L).toInt()
    }

    fun supports(w: Int, h: Int, fps: Int): Boolean {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        return list.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals("video/avc", true) } &&
                runCatching {
                    info.getCapabilitiesForType("video/avc").videoCapabilities
                        .areSizeAndRateSupported(w, h, fps.toDouble())
                }.getOrDefault(false)
        }
    }
}
