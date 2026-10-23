package com.senza.spphotos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Blur latar ringan:
 *  - Konten yang berada DI BELAKANG bilah (mis. grid foto) direkam sekali ke satu GraphicsLayer.
 *  - Tiap "wilayah" (header, bilah bawah, tombol kaca) menggambar salinan kecil layer itu,
 *    diperkecil (downscale) lalu di-blur, jadi biayanya kecil walau di HP kelas menengah.
 *  - Mode progresif: salinan di-mask dengan gradasi sehingga paling kabur di tepi layar
 *    dan memudar jadi tajam ke arah isi (seperti iOS).
 */
class BackdropRegion(
    val layer: GraphicsLayer,
    val bounds: Rect,
    val shape: Shape,
    val blurPx: Float,
    val scale: Float,
    /** 0 = rata, 1 = kuat di atas lalu memudar ke bawah, 2 = kuat di bawah lalu memudar ke atas */
    val fade: Int
)

class BackdropState(val source: GraphicsLayer) {
    /** 1 = blur penuh, 0 = dijeda (mis. saat scroll cepat) → konten digambar langsung, tanpa rekam ulang. */
    var level by mutableFloatStateOf(1f)
    var origin by mutableStateOf(Offset.Zero)
    val regions = mutableStateMapOf<Any, BackdropRegion>()
}

val LocalBackdrop = compositionLocalOf<BackdropState?> { null }

/** Tandai konten yang ada di belakang bilah kaca (sumber blur). */
@Composable
fun Modifier.backdropSource(): Modifier {
    val st = LocalBackdrop.current ?: return this
    return this
        .onGloballyPositioned { st.origin = it.positionInRoot() }
        .drawWithContent {
            val lv = st.level
            if (lv < 0.02f) {
                drawContent()
                return@drawWithContent
            }
            st.source.record(this, layoutDirection, size.toIntSize()) {
                this@drawWithContent.drawContent()
            }
            drawLayer(st.source)
            val o = st.origin
            for (r in st.regions.values) drawRegion(st.source, o, r, lv)
        }
}

/** Daftarkan elemen ini sebagai wilayah blur. */
@Composable
fun Modifier.backdropRegion(
    blur: Dp,
    shape: Shape = RectangleShape,
    scale: Float = 0.25f,
    fade: Int = 0
): Modifier {
    val st = LocalBackdrop.current ?: return this
    val layer = rememberGraphicsLayer()
    val id = remember { Any() }
    val blurPx = with(LocalDensity.current) { blur.toPx() }
    DisposableEffect(id) { onDispose { st.regions.remove(id) } }
    return this.onGloballyPositioned { c ->
        st.regions[id] = BackdropRegion(layer, c.boundsInRoot(), shape, blurPx, scale, fade)
    }
}

private fun DrawScope.drawRegion(src: GraphicsLayer, origin: Offset, r: BackdropRegion, alpha: Float) {
    val left = r.bounds.left - origin.x
    val top = r.bounds.top - origin.y
    val w = r.bounds.width
    val h = r.bounds.height
    if (w < 2f || h < 2f) return
    val s = r.scale
    val lw = max(1, (w * s).roundToInt())
    val lh = max(1, (h * s).roundToInt())
    val layer = r.layer
    layer.renderEffect = BlurEffect(r.blurPx * s, r.blurPx * s, TileMode.Clamp)
    layer.alpha = alpha
    layer.compositingStrategy = CompositingStrategy.Offscreen
    layer.record(this, layoutDirection, IntSize(lw, lh)) {
        scale(s, s, pivot = Offset.Zero) {
            translate(-left, -top) { drawLayer(src) }
        }
        if (r.fade != 0) {
            val brush = if (r.fade == 1)
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = 0f, endY = lh.toFloat())
            else
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = lh.toFloat())
            drawRect(brush, size = Size(lw.toFloat(), lh.toFloat()), blendMode = BlendMode.DstIn)
        }
    }
    val outline = r.shape.createOutline(Size(w, h), layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    translate(left, top) {
        clipPath(path) {
            scale(w / lw, h / lh, pivot = Offset.Zero) { drawLayer(layer) }
        }
    }
}

/**
 * Blur progresif ala iOS untuk tepi atas / bawah layar. Isi Box ini kosong; letakkan sebagai
 * latar header/bilah. [fade] 1 = kuat di atas, 2 = kuat di bawah.
 */
@Composable
fun ProgressiveBlur(modifier: Modifier = Modifier, fade: Int, scrim: Color) {
    Box(modifier) {
        // tiga lapis dengan radius berbeda → transisi halus dari kabur ke tajam
        Box(Modifier.matchParentSize().backdropRegion(30.dp, fade = fade, scale = 0.2f))
        Box(Modifier.matchParentSize().backdropRegion(15.dp, fade = fade, scale = 0.25f))
        Box(Modifier.matchParentSize().backdropRegion(6.dp, fade = fade, scale = 0.4f))
        // gradasi gelap/terang tipis seperti di iOS
        Box(
            Modifier.matchParentSize().background(
                if (fade == 1) Brush.verticalGradient(listOf(scrim.copy(alpha = 0.60f), scrim.copy(alpha = 0.22f), Color.Transparent))
                else Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = 0.18f), scrim.copy(alpha = 0.50f)))
            )
        )
    }
}

/**
 * Jeda blur saat daftar sedang di-scroll agar frame stabil di refresh rate tinggi (90/120 Hz);
 * blur muncul lagi dengan halus setelah berhenti. [blurWhileScrolling]=true → blur tetap menyala.
 */
@Composable
fun BackdropScrollPause(scrolling: () -> Boolean, blurWhileScrolling: Boolean) {
    val st = LocalBackdrop.current ?: return
    LaunchedEffect(st, blurWhileScrolling) {
        snapshotFlow { scrolling() }.collectLatest { sc ->
            val target = if (sc && !blurWhileScrolling) 0f else 1f
            val a = Animatable(st.level)
            a.animateTo(target, tween(if (target == 0f) 50 else 240)) { st.level = value }
        }
    }
}
