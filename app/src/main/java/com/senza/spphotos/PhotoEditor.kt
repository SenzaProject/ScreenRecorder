@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.senza.spphotos

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val IosYellow = Color(0xFFFFD60A)
private val IosGray = Color(0xFF9A9AA0)
private val IosCircle = Color(0xFF232325)

// kind glyph tambahan untuk Crop & Auto
private const val G_STRAIGHTEN = 15
private const val G_VERTICAL = 16
private const val G_HORIZONTAL = 17
private const val G_WAND = 18

private fun DrawScope.drawGlyph(kind: Int, c: Color) {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val sw = s * 0.075f
    val st = Stroke(width = sw, cap = StrokeCap.Round)
    val r = s * 0.34f
    val tl = Offset(cx - r, cy - r)
    val sz = Size(r * 2, r * 2)
    fun ring() = drawCircle(c, r, Offset(cx, cy), style = st)
    when (kind) {
        A_EXP -> {
            ring()
            drawLine(c, Offset(cx - r * 0.7f, cy + r * 0.7f), Offset(cx + r * 0.7f, cy - r * 0.7f), sw, StrokeCap.Round)
            drawLine(c, Offset(cx - r * 0.55f, cy - r * 0.35f), Offset(cx - r * 0.15f, cy - r * 0.35f), sw * 0.8f, StrokeCap.Round)
            drawLine(c, Offset(cx - r * 0.35f, cy - r * 0.55f), Offset(cx - r * 0.35f, cy - r * 0.15f), sw * 0.8f, StrokeCap.Round)
            drawLine(c, Offset(cx + r * 0.15f, cy + r * 0.4f), Offset(cx + r * 0.55f, cy + r * 0.4f), sw * 0.8f, StrokeCap.Round)
        }
        A_BRL -> {
            val p = Path().apply {
                moveTo(cx, cy - r * 1.05f); quadraticBezierTo(cx, cy, cx + r * 1.05f, cy)
                quadraticBezierTo(cx, cy, cx, cy + r * 1.05f); quadraticBezierTo(cx, cy, cx - r * 1.05f, cy)
                quadraticBezierTo(cx, cy, cx, cy - r * 1.05f); close()
            }
            drawPath(p, c)
        }
        A_HIL -> { ring(); drawArc(c, -90f, 90f, true, tl, sz) }
        A_SHA -> { ring(); drawArc(c, 90f, 180f, true, tl, sz) }
        A_CON -> { ring(); drawArc(c, -90f, 180f, true, tl, sz) }
        A_BRT -> {
            drawCircle(c, r * 0.5f, Offset(cx, cy), style = st)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val ca = kotlin.math.cos(a).toFloat(); val sa = kotlin.math.sin(a).toFloat()
                drawLine(c, Offset(cx + ca * r * 0.8f, cy + sa * r * 0.8f), Offset(cx + ca * r * 1.1f, cy + sa * r * 1.1f), sw, StrokeCap.Round)
            }
        }
        A_BLK -> { ring(); drawCircle(c, r * 0.45f, Offset(cx, cy)) }
        A_SAT -> {
            drawCircle(c, r * 0.58f, Offset(cx, cy - r * 0.38f), style = st)
            drawCircle(c, r * 0.58f, Offset(cx - r * 0.42f, cy + r * 0.3f), style = st)
            drawCircle(c, r * 0.58f, Offset(cx + r * 0.42f, cy + r * 0.3f), style = st)
        }
        A_VIB -> {
            ring()
            val p = Path().apply { moveTo(cx - r * 0.5f, cy - r * 0.35f); lineTo(cx, cy + r * 0.45f); lineTo(cx + r * 0.5f, cy - r * 0.35f) }
            drawPath(p, c, style = st)
        }
        A_WRM -> {
            drawLine(c, Offset(cx, cy - r * 1.0f), Offset(cx, cy + r * 0.3f), sw * 1.6f, StrokeCap.Round)
            drawCircle(c, r * 0.5f, Offset(cx, cy + r * 0.55f))
            drawCircle(c, r * 0.5f, Offset(cx, cy + r * 0.55f), style = st)
        }
        A_TNT -> {
            val p = Path().apply {
                moveTo(cx, cy - r); cubicTo(cx + r * 0.2f, cy - r * 0.4f, cx + r * 0.9f, cy - r * 0.1f, cx + r * 0.9f, cy + r * 0.35f)
                cubicTo(cx + r * 0.9f, cy + r * 0.9f, cx - r * 0.9f, cy + r * 0.9f, cx - r * 0.9f, cy + r * 0.35f)
                cubicTo(cx - r * 0.9f, cy - r * 0.1f, cx - r * 0.2f, cy - r * 0.4f, cx, cy - r); close()
            }
            drawPath(p, c, style = st)
        }
        A_SHP -> {
            val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy + r * 0.8f); lineTo(cx - r, cy + r * 0.8f); close() }
            drawPath(p, c, style = st)
        }
        A_DEF -> {
            val p = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy + r * 0.8f); lineTo(cx - r, cy + r * 0.8f); close() }
            drawPath(p, c, style = st)
            val q = Path().apply { moveTo(cx, cy - r * 0.15f); lineTo(cx + r * 0.4f, cy + r * 0.55f); lineTo(cx - r * 0.4f, cy + r * 0.55f); close() }
            drawPath(q, c)
        }
        A_NR -> {
            for (i in -1..1) for (j in -1..1) drawCircle(c, sw * 0.65f, Offset(cx + i * r * 0.7f, cy + j * r * 0.7f))
        }
        A_VIG -> {
            drawRoundRect(c, Offset(cx - r, cy - r), Size(r * 2, r * 2), androidx.compose.ui.geometry.CornerRadius(r * 0.5f), st)
            drawCircle(c.copy(alpha = 0.55f), r * 0.5f, Offset(cx, cy))
        }
        G_STRAIGHTEN -> {
            drawLine(c, Offset(cx - r * 1.0f, cy + r * 0.45f), Offset(cx + r * 1.0f, cy - r * 0.45f), sw, StrokeCap.Round)
            drawArc(c, 0f, -24f, false, Offset(cx - r * 0.6f, cy - r * 0.6f), Size(r * 1.2f, r * 1.2f), style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
        }
        G_VERTICAL -> {
            val p = Path().apply { moveTo(cx - r * 0.45f, cy - r * 0.9f); lineTo(cx + r * 0.45f, cy - r * 0.9f); lineTo(cx + r, cy + r * 0.9f); lineTo(cx - r, cy + r * 0.9f); close() }
            drawPath(p, c, style = st)
        }
        G_HORIZONTAL -> {
            val p = Path().apply { moveTo(cx - r * 0.9f, cy - r * 0.45f); lineTo(cx + r * 0.9f, cy - r); lineTo(cx + r * 0.9f, cy + r); lineTo(cx - r * 0.9f, cy + r * 0.45f); close() }
            drawPath(p, c, style = st)
        }
        G_WAND -> {
            drawLine(c, Offset(cx - r, cy + r), Offset(cx + r * 0.35f, cy - r * 0.35f), sw * 1.4f, StrokeCap.Round)
            drawLine(c, Offset(cx + r * 0.65f, cy - r * 0.95f), Offset(cx + r * 0.65f, cy - r * 0.55f), sw * 0.8f, StrokeCap.Round)
            drawLine(c, Offset(cx + r * 0.45f, cy - r * 0.75f), Offset(cx + r * 0.85f, cy - r * 0.75f), sw * 0.8f, StrokeCap.Round)
        }
    }
}

@Composable
private fun ToolCircle(kind: Int, value: Float, selected: Boolean, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(68.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (selected) Color(0xFF3A3A3C) else IosCircle),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 2.6.dp.toPx()
                val inset = sw / 2f
                if (abs(value) > 0.002f) {
                    drawArc(
                        IosYellow, -90f, value.coerceIn(-1f, 1f) * 360f, false,
                        Offset(inset, inset), Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
            }
            Canvas(Modifier.size(28.dp)) { drawGlyph(kind, if (selected) Color.White else IosGray) }
        }
        Text(
            label, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
            color = if (selected) Color.White else IosGray, modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Dial/penggaris geser ala iOS: tarik ke kiri/kanan, ada snap ke 0 dan getaran halus. */
@Composable
private fun Dial(
    value: Float,
    min: Float,
    max: Float,
    label: String,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val tickPx = with(density) { 7.dp.toPx() }
    val unit = (max - min) / (if (min < 0f) 200f else 100f)
    val cur by rememberUpdatedState(value)
    var lastTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (abs(value) > 0.001f) IosYellow else Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier.fillMaxWidth().height(46.dp).pointerInput(min, max) {
                detectHorizontalDragGestures(
                    onDragEnd = { onFinish() },
                    onDragCancel = { onFinish() }
                ) { change, dx ->
                    change.consume()
                    var nv = (cur - dx / tickPx * unit).coerceIn(min, max)
                    if (min < 0f && abs(nv) < unit * 1.5f) nv = 0f
                    val idx = (nv / unit).roundToInt()
                    if (idx != lastTick && idx % 5 == 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastTick = idx
                    onChange(nv)
                }
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val base = floor(value / unit).toInt()
                val frac = value / unit - base
                val n = (size.width / tickPx / 2f).toInt() + 2
                for (k in -n..n) {
                    val idx = base + k
                    val v = idx * unit
                    if (v < min - 1e-4f || v > max + 1e-4f) continue
                    val x = cx + (k - frac) * tickPx
                    val major = idx % 10 == 0
                    val hh = if (major) 20.dp.toPx() else 11.dp.toPx()
                    val fade = (1f - abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f).pow(0.6f)
                    drawLine(
                        Color.White.copy(alpha = fade * (if (major) 0.9f else 0.45f)),
                        Offset(x, size.height * 0.5f - hh / 2f), Offset(x, size.height * 0.5f + hh / 2f),
                        1.6.dp.toPx(), StrokeCap.Round
                    )
                }
                drawLine(IosYellow, Offset(cx, size.height * 0.12f), Offset(cx, size.height * 0.88f), 2.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

private fun hit(r: RectF, p: Offset, th: Float): Int {
    val nl = abs(p.x - r.left) < th
    val nr = abs(p.x - r.right) < th
    val nt = abs(p.y - r.top) < th
    val nb = abs(p.y - r.bottom) < th
    val inX = p.x > r.left - th && p.x < r.right + th
    val inY = p.y > r.top - th && p.y < r.bottom + th
    return when {
        nl && nt -> 1
        nr && nt -> 2
        nl && nb -> 3
        nr && nb -> 4
        nl && inY -> 6
        nr && inY -> 7
        nt && inX -> 8
        nb && inX -> 9
        r.contains(p.x, p.y) -> 5
        else -> 0
    }
}

private fun applyDrag(rect: RectF, h: Int, dx: Float, dy: Float, ar: Float?, bd: RectF, mn: Float): RectF {
    val r = RectF(rect)
    when (h) {
        5 -> {
            var ddx = dx
            var ddy = dy
            if (r.left + ddx < bd.left) ddx = bd.left - r.left
            if (r.right + ddx > bd.right) ddx = bd.right - r.right
            if (r.top + ddy < bd.top) ddy = bd.top - r.top
            if (r.bottom + ddy > bd.bottom) ddy = bd.bottom - r.bottom
            r.offset(ddx, ddy)
        }
        6, 7, 8, 9 -> {
            if (ar != null) return applyDrag(rect, 5, dx, dy, ar, bd, mn)
            when (h) {
                6 -> r.left = (r.left + dx).coerceIn(bd.left, rect.right - mn)
                7 -> r.right = (r.right + dx).coerceIn(rect.left + mn, bd.right)
                8 -> r.top = (r.top + dy).coerceIn(bd.top, rect.bottom - mn)
                else -> r.bottom = (r.bottom + dy).coerceIn(rect.top + mn, bd.bottom)
            }
        }
        1, 2, 3, 4 -> {
            when (h) {
                1 -> { r.left += dx; r.top += dy }
                2 -> { r.right += dx; r.top += dy }
                3 -> { r.left += dx; r.bottom += dy }
                else -> { r.right += dx; r.bottom += dy }
            }
            if (h == 1 || h == 3) r.left = r.left.coerceIn(bd.left, rect.right - mn)
            if (h == 2 || h == 4) r.right = r.right.coerceIn(rect.left + mn, bd.right)
            if (h == 1 || h == 2) r.top = r.top.coerceIn(bd.top, rect.bottom - mn)
            if (h == 3 || h == 4) r.bottom = r.bottom.coerceIn(rect.top + mn, bd.bottom)
            if (ar != null) {
                var hh = r.width() / ar
                if (h == 1 || h == 2) r.top = r.bottom - hh else r.bottom = r.top + hh
                if (r.top < bd.top) {
                    r.top = bd.top; hh = r.bottom - r.top
                    val ww = hh * ar
                    if (h == 1 || h == 3) r.left = r.right - ww else r.right = r.left + ww
                }
                if (r.bottom > bd.bottom) {
                    r.bottom = bd.bottom; hh = r.bottom - r.top
                    val ww = hh * ar
                    if (h == 1 || h == 3) r.left = r.right - ww else r.right = r.left + ww
                }
            }
        }
    }
    return r
}

@Composable
private fun CropOverlay(
    full: Bitmap,
    geo: Geo,
    ratio: Float?,
    onChange: (Float, Float, Float, Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val bw = full.width.toFloat()
        val bh = full.height.toFloat()
        val sc = min(wPx / bw, hPx / bh)
        val iw = bw * sc
        val ih = bh * sc
        val iLeft = (wPx - iw) / 2f
        val iTop = (hPx - ih) / 2f
        val rect = RectF(iLeft + geo.l * iw, iTop + geo.t * ih, iLeft + geo.r * iw, iTop + geo.b * ih)
        val rectNow by rememberUpdatedState(rect)
        var handle by remember { mutableIntStateOf(0) }
        var dragging by remember { mutableStateOf(false) }
        val th = with(density) { 38.dp.toPx() }
        val minPx = with(density) { 64.dp.toPx() }

        Image(
            full.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit
        )
        Canvas(
            Modifier.fillMaxSize().pointerInput(full, ratio, wPx, hPx) {
                detectDragGestures(
                    onDragStart = { p -> handle = hit(rectNow, p, th); dragging = true },
                    onDragEnd = { dragging = false; handle = 0; onFinish() },
                    onDragCancel = { dragging = false; handle = 0; onFinish() }
                ) { change, drag ->
                    change.consume()
                    if (handle != 0) {
                        val bd = RectF(iLeft, iTop, iLeft + iw, iTop + ih)
                        val nr = applyDrag(rectNow, handle, drag.x, drag.y, ratio, bd, minPx)
                        onChange((nr.left - iLeft) / iw, (nr.top - iTop) / ih, (nr.right - iLeft) / iw, (nr.bottom - iTop) / ih)
                    }
                }
            }
        ) {
            val dim = Color.Black.copy(alpha = 0.58f)
            drawRect(dim, Offset(iLeft, iTop), Size(iw, rect.top - iTop))
            drawRect(dim, Offset(iLeft, rect.bottom), Size(iw, iTop + ih - rect.bottom))
            drawRect(dim, Offset(iLeft, rect.top), Size(rect.left - iLeft, rect.height()))
            drawRect(dim, Offset(rect.right, rect.top), Size(iLeft + iw - rect.right, rect.height()))
            drawRect(Color.White, Offset(rect.left, rect.top), Size(rect.width(), rect.height()), style = Stroke(1.dp.toPx()))
            if (dragging) {
                val g = Color.White.copy(alpha = 0.45f)
                for (i in 1..2) {
                    val x = rect.left + rect.width() * i / 3f
                    val y = rect.top + rect.height() * i / 3f
                    drawLine(g, Offset(x, rect.top), Offset(x, rect.bottom), 0.8.dp.toPx())
                    drawLine(g, Offset(rect.left, y), Offset(rect.right, y), 0.8.dp.toPx())
                }
            }
            val len = 22.dp.toPx()
            val t = 4.dp.toPx()
            fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                drawLine(Color.White, Offset(x - sx * t / 2f, y), Offset(x + sx * len, y), t, StrokeCap.Square)
                drawLine(Color.White, Offset(x, y - sy * t / 2f), Offset(x, y + sy * len), t, StrokeCap.Square)
            }
            corner(rect.left, rect.top, 1f, 1f)
            corner(rect.right, rect.top, -1f, 1f)
            corner(rect.left, rect.bottom, 1f, -1f)
            corner(rect.right, rect.bottom, -1f, -1f)
        }
    }
}

private class ToneCache {
    var key: Triple<Adj, Int, Float>? = null
    var bmp: Bitmap? = null
}

private val ASPECT_LABELS = listOf("Freeform", "Original", "Square", "16:9", "4:3", "3:2", "5:4", "7:5", "5:3")
private val ASPECT_LONG_SHORT = listOf(0f, 0f, 1f, 16f / 9f, 4f / 3f, 3f / 2f, 5f / 4f, 7f / 5f, 5f / 3f)

private fun aspectRatioFor(idx: Int, fw: Int, fh: Int): Float? {
    if (idx == 0) return null
    if (idx == 1) return fw.toFloat() / fh
    val ls = ASPECT_LONG_SHORT[idx]
    return if (fw >= fh) ls else 1f / ls
}

private fun fitAspect(r: Float, bw: Float, bh: Float): FloatArray {
    val w: Float
    val h: Float
    if (r > bw / bh) { w = bw; h = bw / r } else { h = bh; w = bh * r }
    val l = (1f - w / bw) / 2f
    val t = (1f - h / bh) / 2f
    return floatArrayOf(l, t, 1f - l, 1f - t)
}

@Composable
fun EditorScreen(vm: GalleryVM, m: Media, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var src by remember { mutableStateOf<Bitmap?>(null) }
    var thumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(m.key) {
        val b = withContext(Dispatchers.Default) { try { decodeBitmap(ctx, m.uri, 1280) } catch (e: Throwable) { null } }
        src = b
        if (b != null) {
            thumbs = withContext(Dispatchers.Default) {
                val small = Bitmap.createScaledBitmap(b, 128, (128f * b.height / b.width).toInt().coerceAtLeast(1), true)
                EFILTERS.indices.map { processTone(small, EditState(filter = it)) }
            }
        }
    }

    var st by remember { mutableStateOf(EditState()) }
    var base by remember { mutableStateOf(EditState()) }
    val history = remember { mutableStateListOf<EditState>() }
    val future = remember { mutableStateListOf<EditState>() }
    var mode by remember { mutableIntStateOf(0) }
    var selAdj by remember { mutableIntStateOf(0) }
    var selGeo by remember { mutableIntStateOf(0) }
    var compare by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf<Pair<Bitmap, Bitmap>?>(null) }
    val cache = remember { ToneCache() }

    fun commit() {
        if (base != st) { history.add(base); future.clear(); base = st }
    }
    fun undo() {
        if (history.isEmpty()) return
        future.add(st)
        val s = history.removeAt(history.lastIndex)
        st = s; base = s
    }
    fun redo() {
        if (future.isEmpty()) return
        history.add(st)
        val s = future.removeAt(future.lastIndex)
        st = s; base = s
    }

    LaunchedEffect(src) {
        val b = src ?: return@LaunchedEffect
        snapshotFlow { st }.collectLatest { s ->
            val res = withContext(Dispatchers.Default) {
                val key = Triple(s.adj, s.filter, s.filterAmt)
                val cached = cache.bmp
                val toned = if (cache.key == key && cached != null) cached
                else processTone(b, s).also { cache.key = key; cache.bmp = it }
                val full = applyGeometry(toned, s.geo)
                full to cropGeometry(full, s.geo)
            }
            rendered = res
        }
    }

    fun resetCrop(g: Geo) = g.copy(l = 0f, t = 0f, r = 1f, b = 1f, aspect = 0)

    fun setAspect(idx: Int) {
        val full = rendered?.first ?: return
        val ratio = aspectRatioFor(idx, full.width, full.height)
        val box = if (ratio == null) floatArrayOf(st.geo.l, st.geo.t, st.geo.r, st.geo.b)
        else fitAspect(ratio, full.width.toFloat(), full.height.toFloat())
        st = st.copy(geo = st.geo.copy(aspect = idx, l = box[0], t = box[1], r = box[2], b = box[3]))
        commit()
    }

    val edited = st != EditState()

    CompositionLocalProvider(
        LocalGlass provides GlassStyle(vm.glass, vm.reduce, true, vm.blurOn, vm.refractOn),
        LocalContentColor provides Color.White
    ) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            // Bilah atas
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassIconButton(Icons.Rounded.Close, "Batal", onClose, size = 44, blur = false)
                Spacer(Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Undo, "Undo", { undo() }, size = 44, blur = false)
                GlassIconButton(Icons.Rounded.Redo, "Redo", { redo() }, size = 44, blur = false)
                if (edited) GlassChip("Revert", false, {
                    st = EditState(); base = st; history.clear(); future.clear()
                })
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(IosYellow)
                        .clickable(enabled = !saving) {
                            scope.launch {
                                saving = true
                                val ok = saveEdit(ctx, m, st)
                                saving = false
                                Toast.makeText(
                                    ctx,
                                    if (ok) "Disimpan sebagai salinan di Pictures/SP Photos" else "Gagal menyimpan",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) onSaved()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }

            // Area gambar (tahan untuk membandingkan dengan aslinya)
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    .then(
                        if (mode != 2) Modifier.pointerInput(Unit) {
                            detectTapGestures(onPress = { compare = true; tryAwaitRelease(); compare = false })
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val r = rendered
                val original = src
                when {
                    r == null || original == null -> CircularProgressIndicator(color = Color.White)
                    compare -> Image(original.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                    mode == 2 -> CropOverlay(
                        full = r.first, geo = st.geo,
                        ratio = aspectRatioFor(st.geo.aspect, r.first.width, r.first.height),
                        onChange = { l, t, rr, b -> st = st.copy(geo = st.geo.copy(l = l, t = t, r = rr, b = b)) },
                        onFinish = { commit() },
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Image(r.second.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit)
                }
            }

            // Panel alat
            Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.TopCenter) {
                when (mode) {
                    0 -> Column {
                        val v = st.adj.v[selAdj]
                        Dial(
                            value = v, min = ADJ_MIN[selAdj], max = 1f,
                            label = "${(v * 100).roundToInt()}",
                            onChange = { st = st.copy(adj = st.adj.with(selAdj, it)) },
                            onFinish = { commit() }
                        )
                        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                            item {
                                ToolCircle(G_WAND, 0f, false, "Auto") {
                                    src?.let { st = st.copy(adj = autoAdjust(it)); commit() }
                                }
                            }
                            itemsIndexed(ADJ_NAMES) { i, n ->
                                ToolCircle(i, st.adj.v[i], i == selAdj, n.substringBefore(' ')) { selAdj = i }
                            }
                        }
                    }
                    1 -> Column(Modifier.padding(top = 4.dp)) {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(EFILTERS) { i, f ->
                                Column(
                                    Modifier.clickable { st = st.copy(filter = i, filterAmt = 1f); commit() },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)).background(IosCircle)
                                            .border(
                                                if (st.filter == i) 2.dp else 0.dp,
                                                if (st.filter == i) Color.White else Color.Transparent,
                                                RoundedCornerShape(12.dp)
                                            )
                                    ) {
                                        thumbs.getOrNull(i)?.let {
                                            Image(it.asImageBitmap(), f.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Text(
                                        f.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                                        color = if (st.filter == i) Color.White else IosGray
                                    )
                                }
                            }
                        }
                        if (st.filter != 0) {
                            Slider(
                                value = st.filterAmt,
                                onValueChange = { st = st.copy(filterAmt = it) },
                                onValueChangeFinished = { commit() },
                                modifier = Modifier.padding(horizontal = 28.dp),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = IosYellow)
                            )
                        }
                    }
                    else -> Column {
                        val g = st.geo
                        val gv = when (selGeo) { 0 -> g.straighten / 45f; 1 -> g.vertical; else -> g.horizontal }
                        Dial(
                            value = gv, min = -1f, max = 1f,
                            label = if (selGeo == 0) "%.1f°".format(g.straighten) else "${(gv * 100).roundToInt()}",
                            onChange = {
                                st = st.copy(
                                    geo = when (selGeo) {
                                        0 -> g.copy(straighten = it * 45f)
                                        1 -> g.copy(vertical = it)
                                        else -> g.copy(horizontal = it)
                                    }
                                )
                            },
                            onFinish = { commit() }
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolCircle(G_STRAIGHTEN, g.straighten / 45f, selGeo == 0, "Straighten") { selGeo = 0 }
                            ToolCircle(G_VERTICAL, g.vertical, selGeo == 1, "Vertical") { selGeo = 1 }
                            ToolCircle(G_HORIZONTAL, g.horizontal, selGeo == 2, "Horizontal") { selGeo = 2 }
                            Spacer(Modifier.weight(1f))
                            GlassIconButton(Icons.Rounded.RotateLeft, "Putar", {
                                st = st.copy(geo = resetCrop(g.copy(rot90 = (g.rot90 + 3) % 4))); commit()
                            }, size = 44, blur = false)
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(Icons.Rounded.Flip, "Balik", {
                                st = st.copy(geo = resetCrop(g.copy(flip = !g.flip))); commit()
                            }, size = 44, blur = false)
                        }
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            itemsIndexed(ASPECT_LABELS) { i, n -> GlassChip(n, g.aspect == i, { setAspect(i) }) }
                        }
                    }
                }
            }

            // Pemilih mode dengan lensa kaca cair
            Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp)) {
                LiquidSelector(
                    listOf(
                        NavItem("Adjust", Icons.Rounded.Tune),
                        NavItem("Filters", Icons.Rounded.Palette),
                        NavItem("Crop", Icons.Rounded.Crop)
                    ),
                    mode, false, { mode = it }, Modifier.fillMaxWidth(), height = 62.dp
                )
            }
        }

        if (saving) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Menyimpan…") },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        }
    }
}
