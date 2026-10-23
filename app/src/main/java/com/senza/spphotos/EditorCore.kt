package com.senza.spphotos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---- indeks penyesuaian (urutan sama seperti Foto iOS) ----
const val A_EXP = 0
const val A_BRL = 1
const val A_HIL = 2
const val A_SHA = 3
const val A_CON = 4
const val A_BRT = 5
const val A_BLK = 6
const val A_SAT = 7
const val A_VIB = 8
const val A_WRM = 9
const val A_TNT = 10
const val A_SHP = 11
const val A_DEF = 12
const val A_NR = 13
const val A_VIG = 14

val ADJ_NAMES = listOf(
    "Exposure", "Brilliance", "Highlights", "Shadows", "Contrast", "Brightness", "Black Point",
    "Saturation", "Vibrance", "Warmth", "Tint", "Sharpness", "Definition", "Noise Reduction", "Vignette"
)
val ADJ_MIN = listOf(-1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, 0f, 0f, 0f, -1f)

data class Adj(val v: List<Float> = List(15) { 0f }) {
    fun with(i: Int, x: Float): Adj = Adj(v.toMutableList().also { it[i] = x })
}

class FilterSpec(val name: String, val adj: List<Float>, val mono: Float = 0f, val cool: Float = 0f)

private fun spec(vararg p: Pair<Int, Float>): List<Float> {
    val l = MutableList(15) { 0f }
    p.forEach { l[it.first] = it.second }
    return l
}

val EFILTERS = listOf(
    FilterSpec("Original", spec()),
    FilterSpec("Vivid", spec(A_SAT to .28f, A_VIB to .15f, A_CON to .12f, A_BRT to .08f)),
    FilterSpec("Vivid Warm", spec(A_SAT to .28f, A_VIB to .15f, A_CON to .12f, A_WRM to .35f)),
    FilterSpec("Vivid Cool", spec(A_SAT to .28f, A_VIB to .15f, A_CON to .12f, A_WRM to -.35f)),
    FilterSpec("Dramatic", spec(A_CON to .40f, A_SHA to -.20f, A_HIL to -.15f, A_SAT to -.08f, A_BLK to .20f, A_VIG to .25f)),
    FilterSpec("Dramatic Warm", spec(A_CON to .40f, A_SHA to -.20f, A_HIL to -.15f, A_SAT to -.08f, A_BLK to .20f, A_VIG to .25f, A_WRM to .30f)),
    FilterSpec("Dramatic Cool", spec(A_CON to .40f, A_SHA to -.20f, A_HIL to -.15f, A_SAT to -.08f, A_BLK to .20f, A_VIG to .25f, A_WRM to -.30f)),
    FilterSpec("Mono", spec(A_CON to .08f), mono = 1f),
    FilterSpec("Silvertone", spec(A_CON to .12f, A_BRT to .05f), mono = 1f, cool = 1f),
    FilterSpec("Noir", spec(A_CON to .45f, A_BLK to .35f, A_BRT to -.05f), mono = 1f)
)

/** Geometri: rotasi 90°, flip, straighten (derajat), perspektif, dan kotak crop (0..1 pada bingkai hasil geometri). */
data class Geo(
    val rot90: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val vertical: Float = 0f,
    val horizontal: Float = 0f,
    val aspect: Int = 0,
    val l: Float = 0f,
    val t: Float = 0f,
    val r: Float = 1f,
    val b: Float = 1f
)

data class EditState(
    val adj: Adj = Adj(),
    val filter: Int = 0,
    val filterAmt: Float = 1f,
    val geo: Geo = Geo()
)

private fun sstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun buildLut(a: FloatArray): FloatArray {
    val lut = FloatArray(1025)
    val bp = a[A_BLK] * 0.15f
    val gamma = 2f.pow(-a[A_BRT] * 0.8f)
    val cF = 1f + a[A_CON] * 0.8f
    for (i in 0..1024) {
        var y = i / 1024f
        y = ((y - bp) / (1f - bp)).coerceIn(0f, 1f)
        y = y.pow(gamma)
        y = 0.5f + (y - 0.5f) * cF
        val wH = sstep(0.4f, 1f, y)
        val wS = 1f - sstep(0f, 0.6f, y)
        y += a[A_HIL] * 0.25f * wH + a[A_SHA] * 0.25f * wS + a[A_BRL] * (0.15f * wS - 0.10f * wH)
        lut[i] = y.coerceIn(0f, 1f)
    }
    return lut
}

private fun boxBlur(src: IntArray, w: Int, h: Int, r: Int): IntArray {
    val tmp = IntArray(w * h)
    val out = IntArray(w * h)
    val div = 2 * r + 1
    for (y in 0 until h) {
        val row = y * w
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = src[row + i.coerceIn(0, w - 1)]
            sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
        }
        for (x in 0 until w) {
            tmp[row + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = src[row + min(x + r + 1, w - 1)]
            val rem = src[row + max(x - r, 0)]
            sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
            sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
            sb += (add and 255) - (rem and 255)
        }
    }
    for (x in 0 until w) {
        var sr = 0; var sg = 0; var sb = 0
        for (i in -r..r) {
            val c = tmp[i.coerceIn(0, h - 1) * w + x]
            sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
        }
        for (y in 0 until h) {
            out[y * w + x] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
            val add = tmp[min(y + r + 1, h - 1) * w + x]
            val rem = tmp[max(y - r, 0) * w + x]
            sr += ((add shr 16) and 255) - ((rem shr 16) and 255)
            sg += ((add shr 8) and 255) - ((rem shr 8) and 255)
            sb += (add and 255) - (rem and 255)
        }
    }
    return out
}

/** Tone + warna + filter (CPU, multi-thread). Tidak mengubah geometri. */
fun processTone(src: Bitmap, st: EditState): Bitmap {
    val w = src.width
    val h = src.height
    val spec = EFILTERS[st.filter.coerceIn(0, EFILTERS.lastIndex)]
    val amt = st.filterAmt
    val a = FloatArray(15) { st.adj.v[it] + spec.adj[it] * amt }
    val mono = spec.mono * amt
    val cool = spec.cool * amt
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)

    val identity = a.all { abs(it) < 1e-4f } && mono < 1e-4f
    if (identity) return src

    val lut = buildLut(a)
    val m = 2f.pow(a[A_EXP] * 1.5f)
    val cx = w / 2f
    val cy = h / 2f
    val vig = a[A_VIG]
    val threads = 4
    val workers = (0 until threads).map { t ->
        Thread {
            val y0 = h * t / threads
            val y1 = h * (t + 1) / threads
            for (y in y0 until y1) {
                val dyn = (y - cy) / cy
                for (x in 0 until w) {
                    val c = px[y * w + x]
                    var r = ((c shr 16) and 255) / 255f * m
                    var g = ((c shr 8) and 255) / 255f * m
                    var b = (c and 255) / 255f * m
                    val yl = 0.299f * r + 0.587f * g + 0.114f * b
                    val yt = lut[(yl.coerceIn(0f, 1f) * 1024f).toInt()]
                    val tt = sstep(0f, 0.05f, yl)
                    val ratio = if (yl > 1e-4f) min(yt / yl, 6f) else 1f
                    val add = (1f - tt) * (yt - yl)
                    r = tt * r * ratio + (1f - tt) * r + add
                    g = tt * g * ratio + (1f - tt) * g + add
                    b = tt * b * ratio + (1f - tt) * b + add
                    // saturasi + vibrance
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    val mx = max(r, max(g, b))
                    val mn = min(r, min(g, b))
                    val sm = if (mx > 1e-4f) (mx - mn) / mx else 0f
                    val k = max(0f, (1f + a[A_SAT]) * (1f + a[A_VIB] * (1f - sm)))
                    r = gray + (r - gray) * k
                    g = gray + (g - gray) * k
                    b = gray + (b - gray) * k
                    // warmth & tint
                    val wm = a[A_WRM]
                    val tn = a[A_TNT]
                    r *= 1f + 0.15f * wm + 0.06f * tn
                    b *= 1f - 0.15f * wm + 0.06f * tn
                    g *= 1f - 0.12f * tn
                    // mono
                    if (mono > 0f) {
                        val gy = 0.299f * r + 0.587f * g + 0.114f * b
                        r += (gy - r) * mono; g += (gy - g) * mono; b += (gy - b) * mono
                        if (cool > 0f) { b *= 1f + 0.05f * cool; r *= 1f - 0.02f * cool }
                    }
                    // vignette
                    if (vig != 0f) {
                        val dxn = (x - cx) / cx
                        val d = sqrt(dxn * dxn + dyn * dyn) / 1.4142f
                        val f = 1f - vig * 0.8f * sstep(0.35f, 1f, d)
                        r *= f; g *= f; b *= f
                    }
                    val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    px[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }
        }
    }
    workers.forEach { it.start() }
    workers.forEach { it.join() }

    // sharpness / definition / noise reduction
    val sh = a[A_SHP]
    val df = a[A_DEF]
    val nr = a[A_NR]
    var out = px
    if (sh > 0.001f || df > 0.001f || nr > 0.001f) {
        val fine = if (sh > 0.001f) boxBlur(px, w, h, 1) else null
        val coarse = if (df > 0.001f) boxBlur(px, w, h, max(4, min(w, h) / 70)) else null
        val smooth = if (nr > 0.001f) boxBlur(px, w, h, 2) else null
        out = IntArray(w * h)
        for (i in 0 until w * h) {
            val c = px[i]
            var r = ((c shr 16) and 255).toFloat()
            var g = ((c shr 8) and 255).toFloat()
            var b = (c and 255).toFloat()
            if (smooth != null) {
                val s = smooth[i]
                val sr = ((s shr 16) and 255).toFloat(); val sg = ((s shr 8) and 255).toFloat(); val sb = (s and 255).toFloat()
                val diff = (abs(r - sr) + abs(g - sg) + abs(b - sb)) / 765f
                val mask = (1f - diff * 6f).coerceIn(0f, 1f) * nr
                r += (sr - r) * mask; g += (sg - g) * mask; b += (sb - b) * mask
            }
            if (fine != null) {
                val s = fine[i]
                r += sh * 1.5f * (r - ((s shr 16) and 255)); g += sh * 1.5f * (g - ((s shr 8) and 255)); b += sh * 1.5f * (b - (s and 255))
            }
            if (coarse != null) {
                val s = coarse[i]
                r += df * 0.9f * (r - ((s shr 16) and 255)); g += df * 0.9f * (g - ((s shr 8) and 255)); b += df * 0.9f * (b - (s and 255))
            }
            out[i] = (0xFF shl 24) or
                ((r.coerceIn(0f, 255f) + 0.5f).toInt() shl 16) or
                ((g.coerceIn(0f, 255f) + 0.5f).toInt() shl 8) or
                (b.coerceIn(0f, 255f) + 0.5f).toInt()
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

/** Rotasi/flip + straighten + perspektif → bingkai penuh (belum di-crop). */
fun applyGeometry(src: Bitmap, g: Geo): Bitmap {
    var bmp = src
    if (g.rot90 % 4 != 0 || g.flip) {
        val m = Matrix()
        if (g.flip) m.postScale(-1f, 1f)
        m.postRotate(((g.rot90 % 4 + 4) % 4) * 90f)
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
    if (abs(g.straighten) < 0.01f && abs(g.vertical) < 0.001f && abs(g.horizontal) < 0.001f) return bmp
    val w = bmp.width.toFloat()
    val h = bmp.height.toFloat()
    val dxv = g.vertical * 0.18f * w
    val dyh = g.horizontal * 0.18f * h
    val vt = max(dxv, 0f)
    val vb = max(-dxv, 0f)
    val hl = max(dyh, 0f)
    val hr = max(-dyh, 0f)
    val srcPts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
    val dstPts = floatArrayOf(vt, hl, w - vt, hr, w - vb, h - hr, vb, h - hl)
    val m = Matrix()
    m.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
    val th = Math.toRadians(g.straighten.toDouble())
    val s1 = (cos(abs(th)) + sin(abs(th)) * max(w / h, h / w)).toFloat()
    val s2 = 1f / (1f - 0.18f * (abs(g.vertical) + abs(g.horizontal))).coerceAtLeast(0.4f)
    m.postRotate(g.straighten, w / 2f, h / 2f)
    m.postScale(s1 * s2, s1 * s2, w / 2f, h / 2f)
    val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

fun cropGeometry(full: Bitmap, g: Geo): Bitmap {
    val x = (g.l * full.width).toInt().coerceIn(0, full.width - 1)
    val y = (g.t * full.height).toInt().coerceIn(0, full.height - 1)
    val w = ((g.r - g.l) * full.width).toInt().coerceIn(1, full.width - x)
    val h = ((g.b - g.t) * full.height).toInt().coerceIn(1, full.height - y)
    if (x == 0 && y == 0 && w == full.width && h == full.height) return full
    return Bitmap.createBitmap(full, x, y, w, h)
}

/** Perkiraan "Auto" ala Foto iOS dari histogram. */
fun autoAdjust(src: Bitmap): Adj {
    val step = max(1, max(src.width, src.height) / 160)
    val hist = IntArray(256)
    var n = 0
    var sum = 0.0
    var y = 0
    while (y < src.height) {
        var x = 0
        while (x < src.width) {
            val c = src.getPixel(x, y)
            val l = (0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255)).toInt().coerceIn(0, 255)
            hist[l]++; n++; sum += l
            x += step
        }
        y += step
    }
    val mean = (sum / max(n, 1)) / 255.0
    fun pct(p: Double): Int {
        var acc = 0
        for (i in 0..255) { acc += hist[i]; if (acc >= n * p) return i }
        return 255
    }
    val p1 = pct(0.01) / 255f
    val p99 = pct(0.99) / 255f
    var a = Adj()
    val stops = (kotlin.math.ln(0.46 / max(mean, 0.02)) / kotlin.math.ln(2.0)).toFloat() * 0.6f
    a = a.with(A_EXP, (stops / 1.5f).coerceIn(-0.5f, 0.5f))
    a = a.with(A_BLK, ((p1 - 0.02f) * 3f).coerceIn(0f, 0.45f))
    a = a.with(A_CON, 0.10f)
    a = a.with(A_SHA, if (mean < 0.40) 0.25f else 0.08f)
    a = a.with(A_HIL, if (p99 > 0.94f) -0.18f else 0f)
    a = a.with(A_BRL, 0.12f)
    a = a.with(A_SAT, 0.08f)
    a = a.with(A_VIB, 0.15f)
    return a
}

fun decodeBitmap(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
    val src = ImageDecoder.createSource(ctx.contentResolver, uri)
    return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val s = maxOf(w, h)
        if (s > maxSide) {
            val k = maxSide.toFloat() / s
            dec.setTargetSize(maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()))
        }
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}

/** Simpan hasil edit sebagai salinan baru; file asli tidak diubah. */
suspend fun saveEdit(ctx: Context, m: Media, st: EditState): Boolean = withContext(Dispatchers.Default) {
    try {
        val base = decodeBitmap(ctx, m.uri, 4096)
        val toned = processTone(base, st)
        val full = applyGeometry(toned, st.geo)
        val out = cropGeometry(full, st.geo)
        val name = "SP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/SP Photos")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        cv.clear()
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, cv, null, null)
        true
    } catch (e: Throwable) {
        false
    }
}
