package com.senza.spphotos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import coil.compose.AsyncImage

val Accent = Color(0xFF0A84FF)

class GlassStyle(
    val intensity: Float,
    val reduced: Boolean,
    val dark: Boolean,
    val blur: Boolean = true,
    val refract: Boolean = false
)

val LocalGlass = compositionLocalOf { GlassStyle(0.6f, false, true) }

@Composable
fun SPTheme(vm: GalleryVM, content: @Composable () -> Unit) {
    val dark = when (vm.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = Accent, background = Color.Black, surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White
    ) else lightColorScheme(
        primary = Accent, background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color(0xFF111118), onSurface = Color(0xFF111118)
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalGlass provides GlassStyle(vm.glass, vm.reduce, dark, vm.blurOn, vm.refractOn),
            LocalContentColor provides scheme.onBackground
        ) { content() }
    }
}

/**
 * Permukaan "Liquid Glass": blur latar sungguhan (jika ada sumber blur) + scrim tipis,
 * sheen gradasi, kilau spekular kiri-atas, dan tepi bercahaya tidak seragam.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blur: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val g = LocalGlass.current
    val blurred = blur && g.blur && LocalBackdrop.current != null && !g.reduced
    val fg = if (g.dark) Color.White else Color(0xFF111118)
    val fill: Modifier = if (g.reduced) {
        Modifier.background(if (g.dark) Color(0xFF23232C) else Color(0xFFFFFFFF), shape)
    } else {
        val scrim = if (g.dark) Color.Black.copy(alpha = if (blurred) 0.14f else 0.42f)
        else Color.White.copy(alpha = if (blurred) 0.12f else 0.35f)
        val a = if (g.dark) 0.05f + 0.16f * g.intensity else 0.16f + 0.30f * g.intensity
        Modifier
            .background(scrim, shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = a * 1.6f), Color.White.copy(alpha = a * 0.35f))
                ), shape
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = if (g.dark) 0.20f else 0.38f), Color.Transparent),
                        center = Offset(size.width * 0.18f, 0f),
                        radius = maxOf(size.width, size.height) * 0.75f
                    )
                )
            }
    }
    val edge = if (g.reduced) Brush.linearGradient(listOf(Color.White.copy(0.15f), Color.White.copy(0.15f)))
    else Brush.linearGradient(listOf(Color.White.copy(0.85f), Color.White.copy(0.06f), Color.White.copy(0.45f)))
    Box(
        modifier
            .clip(shape)
            .then(if (blurred) Modifier.backdropRegion(8.dp, shape, scale = 0.5f) else Modifier)
            .then(fill)
            .border(1.dp, edge, shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Int = 44,
    blur: Boolean = true
) {
    Glass(modifier.size(size.dp), CircleShape, blur) {
        Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint,
                modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) Accent
                else if (g.dark) Color.White.copy(0.12f) else Color.Black.copy(0.07f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun Thumb(m: Media, modifier: Modifier = Modifier) {
    AsyncImage(
        model = m,
        contentDescription = m.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    m: Media,
    fav: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
    ratio: Float = 1f
) {
    Box(
        modifier
            .aspectRatio(ratio)
            .background(Color(0x33888888))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Thumb(m, Modifier.fillMaxSize())
        if (m.isVideo) {
            Text(
                formatDuration(m.durationMs), color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(0.75f), blurRadius = 6f)
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
            )
        }
        if (fav) {
            Icon(
                Icons.Rounded.Favorite, "Favorit", tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(15.dp)
            )
        }
        if (selected) Box(Modifier.fillMaxSize().background(Accent.copy(alpha = 0.28f)))
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (selected) Accent else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)
                    .background(if (selected) Color.White else Color.Transparent, CircleShape)
            )
        }
    }
}

private val tabs = listOf(
    Icons.Rounded.PhotoLibrary to "Library",
    Icons.Rounded.AutoAwesome to "For You",
    Icons.Rounded.Collections to "Albums",
    Icons.Rounded.Search to "Search"
)

@Composable
fun GlassNavBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val g = LocalGlass.current
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Glass(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(32.dp)) {
            Row(Modifier.fillMaxSize().padding(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEachIndexed { i, (icon, label) ->
                    val sel = i == tab
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                if (sel) (if (g.dark) Color.White.copy(0.22f) else Color.White.copy(0.85f))
                                else Color.Transparent
                            )
                            .clickable { onTab(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, tint = if (sel) Accent else LocalContentColor.current,
                                modifier = Modifier.size(24.dp))
                            Text(
                                label, fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) Accent else LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}
