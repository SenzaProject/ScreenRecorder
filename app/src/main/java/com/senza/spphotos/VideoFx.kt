@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.senza.spphotos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodecList
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Satu klip di timeline. [inMs]/[outMs] = potongan sumber; durasi di timeline = (out-in)/speed. */
data class VClip(
    val id: Long,
    val uri: Uri,
    val srcMs: Long,
    val inMs: Long,
    val outMs: Long,
    val speed: Float = 1f,
    val muted: Boolean = false,
    /** transisi ke klip berikutnya: 0 tanpa, 1 Fade Hitam, 2 Fade Putih, 3 Zoom In, 4 Zoom Out, 5 Geser Kiri, 6 Geser Kanan, 7 Putar */
    val trans: Int = 0,
    val transMs: Long = 500L
) {
    val lenMs: Long get() = ((outMs - inMs) / speed).toLong().coerceAtLeast(100L)
}

/** Layer teks / stiker (emoji) di atas video. x,y = posisi tengah (0..1), size = tinggi font sebagai pecahan tinggi video. */
data class TLayer(
    val id: Long,
    val text: String,
    val font: Int = 1,
    val size: Float = 0.07f,
    val color: Int = 0xFFFFFFFF.toInt(),
    val bg: Boolean = false,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val startMs: Long,
    val endMs: Long,
    val sticker: Boolean = false
)

data class VAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val filter: Int = 0
)

val VFILTERS = listOf("Original", "Mono", "Vivid", "Warm", "Cool", "Fade", "Noir")
val TRANSITIONS = listOf("Tanpa", "Fade Hitam", "Fade Putih", "Zoom In", "Zoom Out", "Geser Kiri", "Geser Kanan", "Putar")

/** Resolusi menurut sisi pendek: (piksel, label). */
val RES_OPTIONS = listOf(
    480 to "480P", 720 to "720P", 1080 to "1080P",
    1440 to "2K", 2160 to "4K", 2880 to "5K", 3456 to "6K", 4320 to "8K"
)

fun clipMediaItem(c: VClip): MediaItem =
    MediaItem.Builder()
        .setUri(c.uri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(c.inMs)
                .setEndPositionMs(c.outMs)
                .build()
        )
        .build()

/** Efek warna/filter (dipakai sama untuk pratinjau & ekspor agar konsisten). */
fun buildColorEffects(a: VAdjust): List<Effect> {
    val e = ArrayList<Effect>()
    var br = a.brightness
    var ct = a.contrast
    var sat = a.saturation
    var wm = a.warmth
    when (a.filter) {
        2 -> { sat += 0.5f; ct += 0.1f }
        3 -> wm += 0.35f
        4 -> wm -= 0.35f
        5 -> { ct -= 0.15f; br += 0.06f }
        6 -> { sat = -1f; ct += 0.3f }
    }
    if (a.filter == 1) e.add(RgbFilter.createGrayscaleFilter())
    if (br != 0f) e.add(Brightness(br.coerceIn(-1f, 1f)))
    if (ct != 0f) e.add(Contrast(ct.coerceIn(-1f, 1f)))
    if (sat != 0f) e.add(HslAdjustment.Builder().adjustSaturation((sat * 100f).coerceIn(-100f, 100f)).build())
    if (wm != 0f) {
        e.add(
            RgbAdjustment.Builder()
                .setRedScale((1f + 0.25f * wm).coerceIn(0f, 2f))
                .setBlueScale((1f - 0.25f * wm).coerceIn(0f, 2f))
                .build()
        )
    }
    return e
}

/** Jam relatif: tahan timestamp frame pertama supaya efek tidak bergantung pada offset urutan. */
private class RelClock {
    var first = -1L
    fun rel(t: Long): Long {
        if (first < 0L) first = t
        return t - first
    }
}

private fun fadeMatrix(k: Float, white: Boolean): FloatArray =
    if (white) floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f, 1f - k, 1f - k, 1f - k, 1f)
    else floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 1f)

/**
 * Transisi di titik potong. [isIn]=true di awal klip, false di akhir klip.
 * v = 1 normal, 0 = sepenuhnya "tertransisi".
 */
private fun transitionEffects(type: Int, isIn: Boolean, durUs: Long, clipLenUs: Long): List<Effect> {
    if (type <= 0 || durUs <= 0L) return emptyList()
    val clock = RelClock()
    fun vis(t: Long): Float {
        val rel = clock.rel(t)
        return if (isIn) (rel.toFloat() / durUs).coerceIn(0f, 1f)
        else ((clipLenUs - rel).toFloat() / durUs).coerceIn(0f, 1f)
    }
    val list = ArrayList<Effect>()
    if (type != 5 && type != 6) {
        list.add(object : RgbMatrix {
            override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
                fadeMatrix(vis(presentationTimeUs), white = type == 2)
        })
    }
    if (type >= 3) {
        list.add(object : MatrixTransformation {
            override fun getMatrix(presentationTimeUs: Long): Matrix {
                val inv = 1f - vis(presentationTimeUs)
                val m = Matrix()
                when (type) {
                    3 -> m.postScale(1f + inv * 0.6f, 1f + inv * 0.6f)
                    4 -> m.postScale(1f - inv * 0.45f, 1f - inv * 0.45f)
                    5 -> m.postTranslate(if (isIn) inv * 2f else -inv * 2f, 0f)
                    6 -> m.postTranslate(if (isIn) -inv * 2f else inv * 2f, 0f)
                    7 -> {
                        val s = 1f - inv * 0.65f
                        m.postScale(s, s)
                        m.postRotate((if (isIn) -1f else 1f) * inv * 180f)
                    }
                }
                return m
            }
        })
    }
    return list
}

/** Overlay semua layer teks/stiker untuk satu item. [itemStartMs] = awal item di timeline. */
private fun overlayEffect(
    ctx: Context,
    layers: List<TLayer>,
    itemStartMs: Long,
    outH: Int
): Effect? {
    val ovs = ArrayList<TextureOverlay>()
    for (L in layers) {
        if (L.text.isBlank()) continue
        val clock = RelClock()
        val tf = if (L.sticker) Typeface.DEFAULT else FontStore.get(ctx, L.font)
        val px = (L.size * outH).roundToInt().coerceAtLeast(10)
        val shown = SpannableString(L.text).apply {
            setSpan(AbsoluteSizeSpan(px), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            setSpan(ForegroundColorSpan(L.color), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            setSpan(TypefaceSpan(tf), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            if (L.bg) setSpan(BackgroundColorSpan(0x99000000.toInt()), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val hidden = SpannableString(" ").apply {
            setSpan(AbsoluteSizeSpan(1), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        val settings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(L.x * 2f - 1f, 1f - L.y * 2f)
            .setOverlayFrameAnchor(0f, 0f)
            .build()
        ovs.add(object : TextOverlay() {
            override fun getText(presentationTimeUs: Long): SpannableString {
                val tl = itemStartMs + clock.rel(presentationTimeUs) / 1000L
                return if (tl >= L.startMs && tl < L.endMs) shown else hidden
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
        })
    }
    return if (ovs.isEmpty()) null else OverlayEffect(ImmutableList.copyOf(ovs))
}

// ---------------- Resolusi & encoder ----------------

private fun evenInt(v: Float): Int = (v.roundToInt() and 1.inv()).coerceAtLeast(2)

/** Tinggi keluaran untuk label resolusi (sisi pendek). Video potret: sisi pendek = lebar. */
fun outHeightFor(shortSide: Int, aspect: Float): Int =
    if (aspect >= 1f) shortSide else evenInt(shortSide / aspect)

fun outWidthFor(outH: Int, aspect: Float): Int = evenInt(outH * aspect)

fun encoderSupports(outH: Int, aspect: Float, mime: String): Boolean {
    val w = outWidthFor(outH, aspect)
    return try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, true) } &&
                try {
                    info.getCapabilitiesForType(mime).videoCapabilities.isSizeSupported(w, outH)
                } catch (_: Exception) {
                    false
                }
        }
    } catch (_: Throwable) {
        false
    }
}

fun resolutionSupported(shortSide: Int, aspect: Float): Boolean {
    if (shortSide <= 1080) return true
    val h = outHeightFor(shortSide, aspect)
    return encoderSupports(h, aspect, MimeTypes.VIDEO_H264) || encoderSupports(h, aspect, MimeTypes.VIDEO_H265)
}

class ExportPlan(val composition: Composition, val mime: String?, val outH: Int)

fun buildExportPlan(
    ctx: Context,
    clips: List<VClip>,
    layers: List<TLayer>,
    adj: VAdjust,
    shortSide: Int,
    aspect: Float
): ExportPlan {
    val outH = outHeightFor(shortSide, aspect)
    var startMs = 0L
    val items = clips.mapIndexed { i, c ->
        val audio: List<AudioProcessor> =
            if (c.speed != 1f) listOf(SonicAudioProcessor().apply { setSpeed(c.speed); setPitch(1f) }) else emptyList()
        val video = ArrayList<Effect>()
        if (c.speed != 1f) video.add(SpeedChangeEffect(c.speed))
        video.addAll(buildColorEffects(adj))
        val lenUs = c.lenMs * 1000L
        if (i > 0) {
            val p = clips[i - 1]
            video.addAll(transitionEffects(p.trans, true, minOf(p.transMs, p.lenMs / 2, c.lenMs / 2) * 1000L, lenUs))
        }
        if (i < clips.lastIndex) {
            val nx = clips[i + 1]
            video.addAll(transitionEffects(c.trans, false, minOf(c.transMs, c.lenMs / 2, nx.lenMs / 2) * 1000L, lenUs))
        }
        video.add(Presentation.createForHeight(outH))
        overlayEffect(ctx, layers, startMs, outH)?.let { video.add(it) }
        startMs += c.lenMs
        EditedMediaItem.Builder(clipMediaItem(c))
            .setRemoveAudio(c.muted)
            .setEffects(Effects(audio, video))
            .build()
    }
    val composition = Composition.Builder(listOf(EditedMediaItemSequence(items))).build()
    val mime = when {
        outH <= 1080 -> null
        encoderSupports(outH, aspect, MimeTypes.VIDEO_H264) -> MimeTypes.VIDEO_H264
        else -> MimeTypes.VIDEO_H265
    }
    return ExportPlan(composition, mime, outH)
}
