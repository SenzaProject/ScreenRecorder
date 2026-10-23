@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

data class NavItem(val label: String, val icon: ImageVector?)

/**
 * Shader lensa kaca cair (Android 13+): membesarkan & membengkokkan isi di bawah lensa,
 * ada pembiasan warna di tepi dan riak yang menguat saat lensa bergerak cepat.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 center;
uniform float2 ext;
uniform float strength;
uniform float wobble;

half4 main(float2 fc) {
    float2 p = fc - center;
    float2 q = abs(p);
    float L = max(ext.x - ext.y, 0.0);
    float dx = max(q.x - L, 0.0);
    float d = length(float2(dx, q.y)) / max(ext.y, 1.0);
    if (d >= 1.0 || strength <= 0.001) {
        return content.eval(fc);
    }
    float edge = smoothstep(0.45, 1.0, d);
    float ripple = sin(d * 20.0) * wobble * 0.05 * (1.0 - d);
    float s = 1.0 - strength * 0.36 * (1.0 - d * d) + ripple;
    float ca = 0.018 * edge * strength;
    half4 cr = content.eval(center + p * (s - ca));
    half4 cg = content.eval(center + p * s);
    half4 cb = content.eval(center + p * (s + ca));
    return half4(cr.r, cg.g, cb.b, max(max(cr.a, cg.a), cb.a));
}
"""

private fun makeLensShader(): Any? =
    if (Build.VERSION.SDK_INT >= 33) {
        try { RuntimeShader(LENS_AGSL) } catch (e: Throwable) { null }
    } else null

@RequiresApi(33)
private fun lensEffect(
    shader: Any, cx: Float, cy: Float, ew: Float, eh: Float, strength: Float, wobble: Float
): androidx.compose.ui.graphics.RenderEffect {
    val s = shader as RuntimeShader
    s.setFloatUniform("center", cx, cy)
    s.setFloatUniform("ext", ew, eh)
    s.setFloatUniform("strength", strength)
    s.setFloatUniform("wobble", wobble)
    return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
}

/** Tampilan fisik lensa: bayangan dalam, kilau spekular atas, kaustik bawah, tepi bercahaya. */
private fun DrawScope.drawLensGlass(p: Float, dark: Boolean) {
    val r = size.height / 2f
    val cr = CornerRadius(r, r)
    val k = 0.35f + 0.65f * p
    // bayangan dalam di tepi atas/bawah (kesan ketebalan kaca)
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = (if (dark) 0.30f else 0.14f) * k),
            0.35f to Color.Transparent,
            0.65f to Color.Transparent,
            1f to Color.Black.copy(alpha = (if (dark) 0.34f else 0.16f) * k)
        ),
        cornerRadius = cr
    )
    // kilau spekular melengkung di atas
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.55f * k), Color.Transparent),
            endY = size.height * 0.36f
        ),
        topLeft = Offset(size.width * 0.10f, size.height * 0.05f),
        size = Size(size.width * 0.80f, size.height * 0.34f),
        cornerRadius = CornerRadius(size.height * 0.17f, size.height * 0.17f)
    )
    // kaustik terang di bawah
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.30f * k)),
            startY = size.height * 0.62f, endY = size.height
        ),
        topLeft = Offset(size.width * 0.08f, size.height * 0.62f),
        size = Size(size.width * 0.84f, size.height * 0.36f),
        cornerRadius = CornerRadius(size.height * 0.18f, size.height * 0.18f)
    )
    // tepi luar bercahaya (terang di kiri-atas & kanan-bawah)
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.40f + 0.55f * p),
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.30f + 0.45f * p)
            ),
            start = Offset.Zero, end = Offset(size.width, size.height)
        ),
        cornerRadius = cr,
        style = Stroke(width = (1.2f + 1.8f * p).dp.toPx())
    )
    // tepi dalam tipis
    drawRoundRect(
        color = Color.White.copy(alpha = 0.10f + 0.12f * p),
        topLeft = Offset(2.5.dp.toPx(), 2.5.dp.toPx()),
        size = Size(size.width - 5.dp.toPx(), size.height - 5.dp.toPx()),
        cornerRadius = CornerRadius(r - 2.5.dp.toPx(), r - 2.5.dp.toPx()),
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * Pemilih Liquid Glass.
 * - Diam: sorotan kaca tipis di belakang tab terpilih.
 * - Ditekan / digeser: sorotan jadi lensa kaca cair yang lebih besar dari bar, membiaskan &
 *   memperbesar isi di bawahnya, mengikuti jari dengan pegas "jelly" (memanjang saat tertinggal
 *   dari jari, memantul saat lepas) dan menempel ke tab terdekat.
 */
@Composable
fun LiquidSelector(
    items: List<NavItem>,
    selected: Int,
    dimmed: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val g = LocalGlass.current
    val n = items.size
    var widthPx by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var fingerX by remember { mutableFloatStateOf(0f) }
    val padPx = with(density) { 5.dp.toPx() }
    val heightPx = with(density) { height.toPx() }
    val slotW = if (widthPx > 0f) (widthPx - 2 * padPx) / n else 0f
    val x = remember { Animatable(0f) }
    val lens = remember(g.refract) { if (g.refract) makeLensShader() else null }

    fun centerOf(i: Int): Float = padPx + slotW * (i + 0.5f)
    fun nearest(px: Float): Int = if (slotW <= 0f) 0 else ((px - padPx) / slotW).toInt().coerceIn(0, n - 1)

    LaunchedEffect(selected, slotW) {
        if (slotW > 0f && !pressed) {
            fingerX = centerOf(selected)
            if (!ready) {
                x.snapTo(centerOf(selected)); ready = true
            } else {
                x.animateTo(centerOf(selected), spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }
    val press by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "lens"
    )
    val nearIdx by remember(slotW) { derivedStateOf { nearest(x.value) } }

    // skala lensa (jelly): membesar saat ditekan + memanjang saat tertinggal dari jari
    fun stretch(): Float = (abs(fingerX - x.value) / (slotW * 0.9f + 1f)).coerceIn(0f, 0.35f)
    fun sx(): Float = (1f + 0.40f * press) * (1f + stretch())
    fun sy(): Float = (1f + 0.46f * press) / (1f + stretch() * 0.55f)

    Box(
        modifier
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(n, slotW) {
                if (slotW <= 0f) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var px = down.position.x
                    val minC = centerOf(0)
                    val maxC = centerOf(n - 1)
                    fingerX = px.coerceIn(minC, maxC)
                    scope.launch {
                        x.animateTo(fingerX, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
                    }
                    var last = nearest(px)
                    do {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        px = c.position.x
                        val tx = px.coerceIn(minC, maxC)
                        fingerX = tx
                        scope.launch {
                            x.animateTo(tx, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium))
                        }
                        val nr = nearest(px)
                        if (nr != last) {
                            last = nr
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        c.consume()
                    } while (ev.changes.any { it.pressed })
                    pressed = false
                    val idx = nearest(px)
                    onSelect(idx)
                    fingerX = centerOf(idx)
                    scope.launch {
                        x.animateTo(centerOf(idx), spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
    ) {
        Glass(Modifier.matchParentSize(), RoundedCornerShape(50)) {}

        val baseWpx = slotW
        val baseHpx = heightPx - 2 * padPx
        val baseWdp = with(density) { baseWpx.toDp() }
        val baseHdp = with(density) { baseHpx.toDp() }

        // Tab statis; pada Android 13+ lapisan ini dibiaskan oleh shader lensa
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= 33 && lens != null && press > 0.01f && slotW > 0f) {
                        renderEffect = lensEffect(
                            lens, x.value, heightPx / 2f,
                            baseWpx * sx() / 2f, baseHpx * sy() / 2f,
                            press, (stretch() / 0.35f).coerceIn(0f, 1f)
                        )
                    } else {
                        renderEffect = null
                    }
                }
                .padding(horizontal = 5.dp)
        ) {
            items.forEachIndexed { i, item ->
                val sel = i == selected && !dimmed
                val under = press > 0.25f && i == nearIdx
                val tint = if (under || (sel && press <= 0.25f)) Accent else LocalContentColor.current
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (item.icon != null) {
                        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(26.dp))
                        Text(
                            item.label, fontSize = 11.sp, color = tint, maxLines = 1,
                            fontWeight = if (sel || under) FontWeight.SemiBold else FontWeight.Medium
                        )
                    } else {
                        Text(
                            item.label, fontSize = 14.sp, color = tint, maxLines = 1,
                            softWrap = false, overflow = TextOverflow.Clip,
                            fontWeight = if (sel || under) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Badan kaca lensa / sorotan
        if (slotW > 0f && ready) {
            val shape = RoundedCornerShape(50)
            val hasShader = Build.VERSION.SDK_INT >= 33 && lens != null
            val base = if (g.dark) Color(0xFF3A3A40) else Color.White
            Box(
                Modifier
                    .size(baseWdp, baseHdp)
                    .graphicsLayer {
                        translationX = x.value - baseWpx / 2f
                        translationY = padPx
                        scaleX = sx()
                        scaleY = sy()
                    }
                    .clip(shape)
                    .background(
                        if (dimmed) Color.Transparent
                        else if (hasShader) base.copy(alpha = 0.26f - 0.16f * press)
                        else base.copy(alpha = (0.30f + 0.62f * press).coerceAtMost(0.92f))
                    )
                    .drawWithContent {
                        drawContent()
                        if (!dimmed) drawLensGlass(press, g.dark)
                    },
                contentAlignment = Alignment.Center
            ) {
                // Cadangan (Android < 13): isi lensa diperbesar manual
                if (!hasShader && press > 0.03f) {
                    val item = items[nearIdx]
                    Column(
                        Modifier.graphicsLayer { alpha = press },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (item.icon != null) {
                            Icon(item.icon, item.label, tint = Accent, modifier = Modifier.size((26f * (1f + 0.3f * press)).dp))
                            Text(item.label, fontSize = (11f + 3f * press).sp, color = Accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        } else {
                            Text(item.label, fontSize = (14f + 3f * press).sp, color = Accent, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** Tombol kaca bulat (mis. Search) dengan efek membesar-memantul saat ditekan. */
@Composable
fun GlassRoundButton(
    icon: ImageVector,
    desc: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 1.18f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "round"
    )
    Glass(
        modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc },
        CircleShape
    ) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = src, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, desc,
                tint = if (active) Accent else LocalContentColor.current,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

@Composable
fun SPBottomBar(
    main: Int,
    searching: Boolean,
    segMode: Boolean,
    libMode: Int,
    onMain: (Int) -> Unit,
    onSeg: (Int) -> Unit,
    onExitSeg: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (segMode && main == 0 && !searching) {
            GlassRoundButton(Icons.Rounded.PhotoLibrary, "Library", true, onExitSeg, size = 56.dp)
            LiquidSelector(
                listOf(NavItem("Years", null), NavItem("Months", null), NavItem("All", null)),
                libMode, false, onSeg, Modifier.weight(1f), height = 56.dp
            )
            GlassRoundButton(Icons.Rounded.Search, "Search", searching, onSearch, size = 56.dp)
        } else {
            LiquidSelector(
                listOf(NavItem("Library", Icons.Rounded.PhotoLibrary), NavItem("Collections", Icons.Rounded.Collections)),
                main, searching, onMain, Modifier.width(250.dp)
            )
            Spacer(Modifier.weight(1f))
            GlassRoundButton(Icons.Rounded.Search, "Search", searching, onSearch)
        }
    }
}
