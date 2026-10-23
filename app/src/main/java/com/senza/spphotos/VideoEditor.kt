@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.Player
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val CapBg = Color(0xFF0E0E10)
private val CapPanel = Color(0xFF1C1C1F)
private val CapCyan = Color(0xFF19E3F2)

private val VFILTER_COLORS = listOf(
    listOf(Color(0xFF8EA1B8), Color(0xFF5B6B80)),
    listOf(Color(0xFFBDBDBD), Color(0xFF424242)),
    listOf(Color(0xFFFF5E62), Color(0xFF3B82F6)),
    listOf(Color(0xFFFFB347), Color(0xFFFF6B6B)),
    listOf(Color(0xFF7FD3FF), Color(0xFF4C6EF5)),
    listOf(Color(0xFFD9D2E9), Color(0xFFB6C0CF)),
    listOf(Color(0xFF9E9E9E), Color(0xFF000000))
)

private val TEXT_COLORS = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A,
    0xFF34C759, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF2D55, 0xFF64D2FF
).map { it.toInt() }

private val EMOJIS = listOf(
    "😀", "😂", "😍", "🥰", "😎", "🤩", "😭", "🔥", "✨", "💯", "❤️", "👍", "🎉", "🌟", "⭐",
    "🎬", "📸", "🎵", "💥", "👑", "🌈", "🍀", "🚀", "💎", "🙌", "💖", "😴", "🤔", "🙈", "👀"
)

private class Snap(val clips: List<VClip>, val layers: List<TLayer>)

private fun fmtT(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

private val thumbCache = android.util.LruCache<String, Bitmap>(200)
private val thumbGate = Semaphore(1)

/** Thumbnail klip: kunci cache per detik sumber → trim tidak memicu decode ulang terus-menerus. */
@Composable
private fun ClipThumbs(clip: VClip, wPx: Float, height: Dp, freeze: Boolean) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val tilePx = with(density) { height.toPx() }
    val n = (wPx / tilePx).toInt().coerceAtLeast(1) + 1
    val frames by produceState<List<Bitmap?>>(
        List(n) { null }, clip.uri, n, clip.inMs / 1000, clip.outMs / 1000, freeze
    ) {
        val ts = List(n) { i ->
            val t = clip.inMs + (((i + 0.5f) / n) * (clip.outMs - clip.inMs)).toLong()
            (t / 1000) * 1000
        }
        val cur = arrayOfNulls<Bitmap>(n)
        for (i in 0 until n) cur[i] = thumbCache.get("${clip.uri}#${ts[i]}")
        value = cur.toList()
        if (freeze || cur.all { it != null }) return@produceState
        thumbGate.withPermit {
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, clip.uri)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 320
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 180
                    val k = 200f / max(w, h)
                    val dw = max(1, (w * k).toInt())
                    val dh = max(1, (h * k).toInt())
                    for (i in 0 until n) {
                        if (cur[i] != null) continue
                        val b = r.getScaledFrameAtTime(ts[i] * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, dw, dh)
                        if (b != null) {
                            thumbCache.put("${clip.uri}#${ts[i]}", b)
                            cur[i] = b
                            value = cur.toList()
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    try { r.release() } catch (_: Throwable) {}
                }
            }
        }
    }
    Row(Modifier.fillMaxSize()) {
        val tileDp = with(density) { (wPx / n).toDp() }
        frames.forEach { b ->
            Box(Modifier.width(tileDp).fillMaxHeight().background(Color(0xFF2A2A2E))) {
                if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ClipBlock(
    clip: VClip,
    selected: Boolean,
    pxPerMs: Float,
    height: Dp,
    freeze: Boolean,
    onSelect: () -> Unit,
    onTrimStart: () -> Unit,
    onTrim: (left: Boolean, dxMs: Float) -> Unit,
    onTrimEnd: () -> Unit
) {
    val density = LocalDensity.current
    val wPx = clip.lenMs * pxPerMs
    Box(
        Modifier.width(with(density) { wPx.toDp() }).height(height)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
    ) {
        ClipThumbs(clip, wPx, height, freeze)
        if (clip.speed != 1f || clip.muted) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(0.6f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clip.speed != 1f) Text("${clip.speed}x", color = Color.White, fontSize = 9.sp)
                if (clip.muted) Icon(Icons.Rounded.VolumeOff, null, tint = Color.White, modifier = Modifier.size(11.dp))
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.dp, Color.White, RoundedCornerShape(6.dp)))
            for (left in listOf(true, false)) {
                Box(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(18.dp).fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .pointerInput(clip.id, left) {
                            detectHorizontalDragGestures(
                                onDragStart = { onTrimStart() },
                                onDragEnd = { onTrimEnd() },
                                onDragCancel = { onTrimEnd() },
                                onHorizontalDrag = { change, dx ->
                                    change.consume()
                                    onTrim(left, dx / pxPerMs)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.width(2.dp).height(16.dp).background(Color(0xFF555555))) }
            }
        }
    }
}

@Composable
private fun LayerBar(
    L: TLayer,
    selected: Boolean,
    pxPerMs: Float,
    onSelect: () -> Unit,
    onStart: () -> Unit,
    onMove: (dxMs: Float) -> Unit,
    onTrim: (left: Boolean, dxMs: Float) -> Unit,
    onEnd: () -> Unit
) {
    val density = LocalDensity.current
    val xDp = with(density) { (L.startMs * pxPerMs).toDp() }
    val wDp = with(density) { ((L.endMs - L.startMs) * pxPerMs).toDp() }
    Box(
        Modifier.offset(x = xDp).width(wDp).height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (L.sticker) Color(0xFF8E5CF7) else Color(0xFF2E6BFF))
            .border(if (selected) 2.dp else 0.dp, Color.White, RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .pointerInput(L.id) {
                detectHorizontalDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                    onHorizontalDrag = { change, dx -> change.consume(); onMove(dx / pxPerMs) }
                )
            }
    ) {
        Text(
            L.text.replace('\n', ' '), maxLines = 1, color = Color.White, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp, end = 12.dp)
        )
        if (selected) {
            for (left in listOf(true, false)) {
                Box(
                    Modifier.align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(11.dp).fillMaxHeight().background(Color.White)
                        .pointerInput(L.id, left) {
                            detectHorizontalDragGestures(
                                onDragStart = { onStart() },
                                onDragEnd = { onEnd() },
                                onDragCancel = { onEnd() },
                                onHorizontalDrag = { change, dx -> change.consume(); onTrim(left, dx / pxPerMs) }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).alpha(if (enabled) 1f else 0.35f).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SmallChip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.alpha(if (enabled) 1f else 0.35f).clip(RoundedCornerShape(50))
            .background(if (selected) CapCyan else Color(0xFF2C2C30))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PanelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(86.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CapCyan)
        )
        Text("${(value * 100).roundToInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(34.dp))
    }
}

@Composable
private fun PanelHeader(title: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.Check, "Selesai", tint = CapCyan, modifier = Modifier.size(26.dp).clickable(onClick = onDone))
    }
}

/** Waktu dipisah ke composable sendiri agar playhead tidak memaksa seluruh editor digambar ulang. */
@Composable
private fun TimeText(pos: () -> Long, total: Long, modifier: Modifier = Modifier) {
    Text("${fmtT(pos())} / ${fmtT(total)}", fontSize = 12.sp, color = Color.White.copy(0.85f), modifier = modifier)
}

@Composable
private fun TextPreview(
    layers: List<TLayer>,
    selLayer: Long?,
    pos: () -> Long,
    boxW: Float,
    boxH: Float,
    onSelect: (Long) -> Unit,
    onDragStart: () -> Unit,
    onMove: (Long, Float, Float) -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val p = pos()
    for (L in layers) {
        if (p < L.startMs || p >= L.endMs) continue
        key(L.id) {
            val fam = if (L.sticker) FontFamily.Default else FontStore.family(ctx, L.font)
            Text(
                L.text, color = Color(L.color), fontFamily = fam,
                fontSize = with(density) { (L.size * boxH).toSp() },
                modifier = Modifier
                    .offset { IntOffset((L.x * boxW).roundToInt(), (L.y * boxH).roundToInt()) }
                    .graphicsLayer { translationX = -size.width / 2f; translationY = -size.height / 2f }
                    .then(if (L.bg) Modifier.background(Color(0x99000000)) else Modifier)
                    .border(if (L.id == selLayer) 1.dp else 0.dp, if (L.id == selLayer) Color.White else Color.Transparent)
                    .pointerInput(L.id, boxW, boxH) {
                        detectDragGestures(onDragStart = { onSelect(L.id); onDragStart() }) { change, drag ->
                            change.consume()
                            onMove(L.id, drag.x / boxW, drag.y / boxH)
                        }
                    }
            )
        }
    }
}

private suspend fun saveVideoToGallery(ctx: Context, file: File): Boolean = withContext(Dispatchers.IO) {
    try {
        val name = "SP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SP Photos")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return@withContext false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        cv.clear()
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
        ctx.contentResolver.update(uri, cv, null, null)
        file.delete()
        true
    } catch (e: Throwable) {
        false
    }
}

@Composable
fun VideoEditorScreen(vm: GalleryVM, start: Media, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val firstLen = start.durationMs.coerceAtLeast(500L)
    val clips = remember { mutableStateListOf(VClip(1L, start.uri, firstLen, 0L, firstLen)) }
    val layers = remember { mutableStateListOf<TLayer>() }
    var nextId by remember { mutableLongStateOf(10L) }
    var selId by remember { mutableStateOf<Long?>(null) }
    var selLayer by remember { mutableStateOf<Long?>(null) }
    var adj by remember { mutableStateOf(VAdjust()) }
    // 0 utama, 1 kecepatan, 2 filter, 3 adjust, 4 teks, 5 stiker, 6 transisi
    var panel by remember { mutableIntStateOf(0) }
    var posMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var trimming by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var exportShort by remember { mutableIntStateOf(1080) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<Snap>() }
    val future = remember { mutableStateListOf<Snap>() }

    val scroll = rememberScrollState()
    val pxPerMs = with(density) { 56.dp.toPx() } / 1000f
    var lastSet by remember { mutableIntStateOf(0) }
    var lastSeekAt by remember { mutableLongStateOf(0L) }
    val total = clips.sumOf { it.lenMs }

    val vidAspect by produceState(16f / 9f, clips.firstOrNull()?.uri) {
        val u = clips.firstOrNull()?.uri ?: return@produceState
        withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, u)
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                value = (if (rot == 90 || rot == 270) h / w else w / h).coerceIn(0.2f, 5f)
            } catch (_: Throwable) {
            } finally {
                try { r.release() } catch (_: Throwable) {}
            }
        }
    }

    val exo = remember {
        val rf = DefaultRenderersFactory(ctx)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        ExoPlayer.Builder(ctx, rf).build()
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    fun mapPos(t: Long): Pair<Int, Long> {
        var acc = 0L
        for ((i, c) in clips.withIndex()) {
            if (t < acc + c.lenMs || i == clips.lastIndex) {
                return i to ((t - acc).coerceIn(0L, c.lenMs) * c.speed).toLong()
            }
            acc += c.lenMs
        }
        return 0 to 0L
    }

    fun seekTimeline(t: Long) {
        if (clips.isEmpty()) return
        val (i, off) = mapPos(t)
        exo.seekTo(i, off)
    }

    fun snapshot() {
        history.add(Snap(clips.toList(), layers.toList()))
        future.clear()
        if (history.size > 60) history.removeAt(0)
    }

    fun restore(s: Snap) {
        clips.clear(); clips.addAll(s.clips)
        layers.clear(); layers.addAll(s.layers)
        selId = null; selLayer = null
    }

    fun undo() {
        if (history.isEmpty()) return
        future.add(Snap(clips.toList(), layers.toList()))
        restore(history.removeAt(history.lastIndex))
    }

    fun redo() {
        if (future.isEmpty()) return
        history.add(Snap(clips.toList(), layers.toList()))
        restore(future.removeAt(future.lastIndex))
    }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun selIndex(): Int = clips.indexOfFirst { it.id == selId }
    fun updateLayer(id: Long, f: (TLayer) -> TLayer) {
        val i = layers.indexOfFirst { it.id == id }
        if (i >= 0) layers[i] = f(layers[i])
    }

    fun split() {
        var acc = 0L
        for ((i, c) in clips.withIndex()) {
            if (posMs > acc + 150 && posMs < acc + c.lenMs - 150) {
                snapshot()
                val cut = c.inMs + ((posMs - acc) * c.speed).toLong()
                clips[i] = c.copy(outMs = cut)
                val b = c.copy(id = nextId++, inMs = cut, trans = c.trans, transMs = c.transMs)
                clips[i] = clips[i].copy(trans = 0)
                clips.add(i + 1, b)
                selId = b.id; selLayer = null
                return
            }
            acc += c.lenMs
        }
        toast("Letakkan playhead di dalam klip untuk memotong")
    }

    fun deleteSelected() {
        val li = layers.indexOfFirst { it.id == selLayer }
        if (li >= 0) { snapshot(); layers.removeAt(li); selLayer = null; panel = 0; return }
        val i = selIndex()
        if (i < 0) { toast("Pilih klip atau layer dulu"); return }
        snapshot(); clips.removeAt(i); selId = null
    }

    fun duplicateSel() {
        val i = selIndex()
        if (i < 0) { toast("Pilih klip dulu"); return }
        snapshot(); clips.add(i + 1, clips[i].copy(id = nextId++, trans = 0))
    }

    fun toggleMute() {
        val i = selIndex()
        if (i < 0) { toast("Pilih klip dulu"); return }
        snapshot(); clips[i] = clips[i].copy(muted = !clips[i].muted)
    }

    fun setSpeed(v: Float) {
        val i = selIndex()
        if (i < 0) return
        snapshot(); clips[i] = clips[i].copy(speed = v)
    }

    fun trimClip(id: Long, left: Boolean, dxTimelineMs: Float) {
        val i = clips.indexOfFirst { it.id == id }
        if (i < 0) return
        val c = clips[i]
        val d = (dxTimelineMs * c.speed).toLong()
        val minLen = (200 * c.speed).toLong()
        clips[i] = if (left) c.copy(inMs = (c.inMs + d).coerceIn(0L, c.outMs - minLen))
        else c.copy(outMs = (c.outMs + d).coerceIn(c.inMs + minLen, c.srcMs))
    }

    fun addLayer(sticker: Boolean, text: String) {
        snapshot()
        val s = posMs.coerceAtMost(max(total - 500L, 0L))
        val e = max(min(total, s + 3000L), s + 500L)
        val L = TLayer(
            nextId++, text, font = if (sticker) -1 else 2, size = if (sticker) 0.16f else 0.07f,
            startMs = s, endMs = e, sticker = sticker
        )
        layers.add(L)
        selLayer = L.id; selId = null
        panel = if (sticker) 0 else 4
    }

    fun moveLayer(id: Long, dxMs: Float) = updateLayer(id) {
        val len = it.endMs - it.startMs
        val ns = (it.startMs + dxMs.toLong()).coerceIn(0L, max(total - len, 0L))
        it.copy(startMs = ns, endMs = ns + len)
    }

    fun trimLayer(id: Long, left: Boolean, dxMs: Float) = updateLayer(id) {
        if (left) it.copy(startMs = (it.startMs + dxMs.toLong()).coerceIn(0L, it.endMs - 300L))
        else it.copy(endMs = (it.endMs + dxMs.toLong()).coerceIn(it.startMs + 300L, max(total, it.startMs + 300L)))
    }

    // ---- Pemutar: bangun ulang playlist HANYA saat struktur berubah dan tidak sedang trim (anti-freeze)
    val playlistKey = clips.map { listOf<Any>(it.id, it.inMs, it.outMs) }
    LaunchedEffect(playlistKey, trimming) {
        if (trimming) return@LaunchedEffect
        if (clips.isEmpty()) { exo.clearMediaItems(); return@LaunchedEffect }
        exo.setMediaItems(clips.map { clipMediaItem(it) }, false)
        exo.prepare()
        seekTimeline(posMs.coerceAtMost(total))
    }
    LaunchedEffect(adj) {
        val fx = buildColorEffects(adj)
        // pratinjau diperkecil dulu ke 720p agar efek ringan di video resolusi tinggi
        exo.setVideoEffects(if (fx.isEmpty()) emptyList() else listOf<Effect>(Presentation.createForHeight(720)) + fx)
    }

    // ---- Putar: playhead mengikuti pemutar (30 Hz)
    LaunchedEffect(playing) {
        if (playing) exo.play() else exo.pause()
        while (playing && clips.isNotEmpty()) {
            val idx = exo.currentMediaItemIndex.coerceIn(0, clips.lastIndex)
            val c = clips[idx]
            if (exo.playbackParameters.speed != c.speed) exo.setPlaybackSpeed(c.speed)
            val vol = if (c.muted) 0f else 1f
            if (exo.volume != vol) exo.volume = vol
            var acc = 0L
            for (k in 0 until idx) acc += clips[k].lenMs
            posMs = (acc + (exo.currentPosition / c.speed).toLong()).coerceAtMost(total)
            val px = (posMs * pxPerMs).roundToInt()
            if (px != scroll.value) { lastSet = px; scroll.scrollTo(px) }
            if (exo.playbackState == Player.STATE_ENDED) playing = false
            delay(33)
        }
    }

    // ---- Geser timeline oleh pengguna → seek di-throttle (anti banjir seek ke decoder)
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.value }.collectLatest { v ->
            if (v == lastSet) return@collectLatest
            if (playing) playing = false
            posMs = (v / pxPerMs).toLong().coerceIn(0L, total)
            val wait = (lastSeekAt + 70L - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            if (wait > 0L) delay(wait)
            lastSeekAt = SystemClock.uptimeMillis()
            exo.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            seekTimeline(posMs)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.isScrollInProgress }.collect { inProgress ->
            if (!inProgress && !playing) {
                exo.setSeekParameters(SeekParameters.EXACT)
                seekTimeline(posMs)
            }
        }
    }

    val sel = selIndex()
    val selLayerObj = layers.firstOrNull { it.id == selLayer }
    val tlHeight = (18 + 66 + min(layers.size, 3) * 28 + 12).dp

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(Modifier.fillMaxSize().background(CapBg).statusBarsPadding().navigationBarsPadding()) {
            // Bilah atas
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Close, "Tutup", Modifier.size(28.dp).clickable(onClick = onClose))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(CapCyan)
                        .clickable { if (clips.isEmpty()) toast("Timeline kosong") else showExport = true }
                        .padding(horizontal = 18.dp, vertical = 7.dp)
                ) { Text("Ekspor", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }

            // Pratinjau (kotak mengikuti rasio video agar posisi/ukuran teks sama dengan hasil ekspor)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
                val fitW = if (maxWidth / maxHeight > vidAspect) maxHeight * vidAspect else maxWidth
                val fitH = fitW / vidAspect
                val boxW = with(density) { fitW.toPx() }
                val boxH = with(density) { fitH.toPx() }
                Box(Modifier.size(fitW, fitH)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            (LayoutInflater.from(c).inflate(R.layout.player_view_texture, null) as PlayerView).also {
                                it.useController = false
                                it.player = exo
                            }
                        }
                    )
                    TextPreview(
                        layers, selLayer, { posMs }, boxW, boxH,
                        onSelect = { id -> selLayer = id; selId = null; if (panel != 4) panel = 4 },
                        onDragStart = { snapshot() },
                        onMove = { id, dx, dy -> updateLayer(id) { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) } }
                    )

                }
                if (clips.isEmpty()) Text("Timeline kosong — tekan +", color = Color.White.copy(0.6f))
            }

            // Waktu + kontrol
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeText({ posMs }, total, Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable {
                        if (clips.isNotEmpty()) {
                            if (!playing && posMs >= total - 50) {
                                posMs = 0; lastSet = 0; seekTimeline(0)
                                scope.launch { scroll.scrollTo(0) }
                            }
                            playing = !playing
                        }
                    },
                    contentAlignment = Alignment.Center
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Putar", Modifier.size(30.dp)) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    Icon(Icons.Rounded.Undo, "Undo", Modifier.size(24.dp).alpha(if (history.isEmpty()) 0.35f else 1f).clickable { undo() })
                    Spacer(Modifier.width(18.dp))
                    Icon(Icons.Rounded.Redo, "Redo", Modifier.size(24.dp).alpha(if (future.isEmpty()) 0.35f else 1f).clickable { redo() })
                }
            }

            // Timeline: ruler + track utama + track layer
            BoxWithConstraints(Modifier.fillMaxWidth().height(tlHeight).background(CapBg)) {
                val half = maxWidth / 2
                val totalDp = with(density) { (total * pxPerMs).toDp() }
                Row(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Spacer(Modifier.width(half))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.height(18.dp)) {
                            val secDp = with(density) { (pxPerMs * 1000f).toDp() }
                            for (s in 0..(total / 1000)) {
                                Box(Modifier.width(secDp)) {
                                    Text(
                                        if (s % 2L == 0L) fmtT(s * 1000) else "·",
                                        color = Color.White.copy(0.45f), fontSize = 9.sp, maxLines = 1, softWrap = false
                                    )
                                }
                            }
                        }
                        Box(Modifier.padding(top = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                clips.forEach { c ->
                                    ClipBlock(
                                        c, c.id == selId, pxPerMs, 58.dp, trimming || playing,
                                        onSelect = { selId = if (selId == c.id) null else c.id; selLayer = null },
                                        onTrimStart = { snapshot(); trimming = true },
                                        onTrim = { left, dx -> trimClip(c.id, left, dx) },
                                        onTrimEnd = { trimming = false }
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                                        .clickable { showPicker = true },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Add, "Tambah klip", tint = Color.Black, modifier = Modifier.size(28.dp)) }
                            }
                            // tombol transisi di titik potong
                            var acc = 0L
                            for (i in 0 until clips.size - 1) {
                                acc += clips[i].lenMs
                                val bx = with(density) { (acc * pxPerMs).toDp() } - 11.dp
                                val has = clips[i].trans != 0
                                Box(
                                    Modifier.offset(x = bx, y = 18.dp).size(22.dp).clip(RoundedCornerShape(6.dp))
                                        .background(if (has) CapCyan else Color.White)
                                        .clickable { selId = clips[i].id; selLayer = null; panel = 6 },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.SwapHoriz, "Transisi", tint = Color.Black, modifier = Modifier.size(16.dp)) }
                            }
                        }
                        layers.forEach { L ->
                            Box(Modifier.padding(top = 4.dp).width(totalDp).height(24.dp)) {
                                LayerBar(
                                    L, L.id == selLayer, pxPerMs,
                                    onSelect = { selLayer = L.id; selId = null; if (!L.sticker) panel = 4 },
                                    onStart = { snapshot() },
                                    onMove = { moveLayer(L.id, it) },
                                    onTrim = { left, dx -> trimLayer(L.id, left, dx) },
                                    onEnd = {}
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(half))
                }
                Box(Modifier.align(Alignment.Center).width(2.dp).fillMaxHeight().background(Color.White))
            }

            // Bilah alat / panel
            Box(Modifier.fillMaxWidth().background(CapPanel)) {
                when (panel) {
                    1 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Kecepatan") { panel = 0 }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)) { _, v ->
                                SmallChip("${v}x", sel >= 0 && clips[sel].speed == v) { setSpeed(v) }
                            }
                        }
                    }
                    2 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Filter") { panel = 0 }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(VFILTERS) { i, n ->
                                Column(Modifier.clickable { adj = adj.copy(filter = i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(62.dp).clip(RoundedCornerShape(10.dp))
                                            .background(Brush.linearGradient(VFILTER_COLORS[i]))
                                            .border(if (adj.filter == i) 2.dp else 0.dp, if (adj.filter == i) CapCyan else Color.Transparent, RoundedCornerShape(10.dp))
                                    )
                                    Text(n, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                    3 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Adjust") { panel = 0 }
                        PanelSlider("Kecerahan", adj.brightness) { adj = adj.copy(brightness = it) }
                        PanelSlider("Kontras", adj.contrast) { adj = adj.copy(contrast = it) }
                        PanelSlider("Saturasi", adj.saturation) { adj = adj.copy(saturation = it) }
                        PanelSlider("Kehangatan", adj.warmth) { adj = adj.copy(warmth = it) }
                    }
                    4 -> Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelHeader("Teks") { panel = 0 }
                        val L = selLayerObj
                        if (L == null) {
                            Text("Pilih layer teks di timeline atau tekan \"Teks\" untuk menambah.", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        } else {
                            TextField(
                                value = L.text, onValueChange = { t -> updateLayer(L.id) { it.copy(text = t) } },
                                maxLines = 3, modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF2C2C30), unfocusedContainerColor = Color(0xFF2C2C30),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(EDITOR_FONTS) { i, f ->
                                    Box(
                                        Modifier.clip(RoundedCornerShape(10.dp))
                                            .background(if (L.font == i) CapCyan else Color(0xFF2C2C30))
                                            .clickable { updateLayer(L.id) { it.copy(font = i) } }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            f.label, fontFamily = FontStore.family(ctx, i), fontSize = 14.sp,
                                            color = if (L.font == i) Color.Black else Color.White, maxLines = 1
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ukuran", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                                Slider(
                                    value = L.size, onValueChange = { v -> updateLayer(L.id) { it.copy(size = v) } },
                                    valueRange = 0.03f..0.25f, modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = CapCyan)
                                )
                                Text("${(L.size * 100).roundToInt()}", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(30.dp))
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(TEXT_COLORS) { _, col ->
                                    Box(
                                        Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                                            .border(if (L.color == col) 3.dp else 1.dp, if (L.color == col) CapCyan else Color(0x55FFFFFF), CircleShape)
                                            .clickable { updateLayer(L.id) { it.copy(color = col) } }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallChip("Latar", L.bg) { updateLayer(L.id) { it.copy(bg = !it.bg) } }
                                SmallChip("Hapus layer", false) { deleteSelected() }
                            }
                        }
                    }
                    5 -> Column(Modifier.padding(14.dp)) {
                        PanelHeader("Stiker") { panel = 0 }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(EMOJIS) { _, e ->
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C30))
                                        .clickable { addLayer(true, e) },
                                    contentAlignment = Alignment.Center
                                ) { Text(e, fontSize = 26.sp) }
                            }
                        }
                    }
                    6 -> Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PanelHeader("Transisi") { panel = 0 }
                        val ci = if (sel >= 0) sel else -1
                        if (ci < 0 || ci >= clips.lastIndex) {
                            Text("Pilih klip yang punya klip berikutnya, atau ketuk tombol ⇄ di titik potong.", color = Color.White.copy(0.7f), fontSize = 13.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(TRANSITIONS) { i, n ->
                                    SmallChip(n, clips[ci].trans == i) { snapshot(); clips[ci] = clips[ci].copy(trans = i) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Durasi", color = Color.White, fontSize = 13.sp)
                                listOf(300L, 500L, 1000L).forEach { d ->
                                    SmallChip("${d / 1000.0}s", clips[ci].transMs == d) { clips[ci] = clips[ci].copy(transMs = d) }
                                }
                            }
                            Text("Transisi terlihat pada hasil ekspor.", color = Color.White.copy(0.55f), fontSize = 11.sp)
                        }
                    }
                    else -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                        ToolButton(Icons.Rounded.ContentCut, "Pisah") { split() }
                        ToolButton(Icons.Rounded.TextFields, "Teks") { addLayer(false, "Teks baru") }
                        ToolButton(Icons.Rounded.EmojiEmotions, "Stiker") { panel = 5 }
                        ToolButton(Icons.Rounded.SwapHoriz, "Transisi") { panel = 6 }
                        ToolButton(Icons.Rounded.Speed, "Kecepatan") { if (sel < 0) toast("Pilih klip dulu") else panel = 1 }
                        ToolButton(if (sel >= 0 && clips[sel].muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Volume") { toggleMute() }
                        ToolButton(Icons.Rounded.AutoAwesome, "Filter") { panel = 2 }
                        ToolButton(Icons.Rounded.Tune, "Adjust") { panel = 3 }
                        ToolButton(Icons.Rounded.ContentCopy, "Duplikat") { duplicateSel() }
                        ToolButton(Icons.Rounded.Delete, "Hapus") { deleteSelected() }
                        ToolButton(Icons.Rounded.MusicNote, "Audio", enabled = false) { toast("Audio: segera hadir") }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val videos = remember(vm.media) { vm.media.filter { it.isVideo } }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Tambah video") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(videos, key = { it.key }) { m ->
                        PhotoTile(m, false, false, false, onClick = {
                            snapshot()
                            val d = m.durationMs.coerceAtLeast(500L)
                            clips.add(VClip(nextId++, m.uri, d, 0L, d))
                            showPicker = false
                        }, onLong = {})
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal") } }
        )
    }

    if (showExport) {
        AlertDialog(
            onDismissRequest = { if (!exporting) showExport = false },
            title = { Text(if (exporting) "Mengekspor…" else "Ekspor video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!exporting) {
                        Text("Resolusi (sisi pendek)", fontSize = 13.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RES_OPTIONS.forEach { (s, label) ->
                                val ok = remember(s, vidAspect) { resolutionSupported(s, vidAspect) }
                                SmallChip(label, exportShort == s && ok, enabled = ok) { exportShort = s }
                            }
                        }
                        Text("Resolusi yang abu-abu tidak didukung encoder perangkat ini. Menaikkan resolusi tidak menambah detail, dan 4K+ butuh waktu & memori besar.", fontSize = 11.sp)
                        Text("Durasi ${fmtT(total)} · disimpan ke Movies/SP Photos", fontSize = 12.sp)
                    } else {
                        LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth())
                        Text("${(exportProgress * 100).roundToInt()}% — jangan tutup aplikasi", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (!exporting) TextButton(onClick = {
                    exporting = true
                    exportProgress = 0f
                    playing = false
                    scope.launch {
                        val short = if (resolutionSupported(exportShort, vidAspect)) exportShort else 1080
                        val out = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
                        val plan = buildExportPlan(
                            ctx, clips.toList(), layers.map { it.copy(endMs = min(it.endMs, total)) },
                            adj, short, vidAspect
                        )
                        val error = suspendCancellableCoroutine<Throwable?> { cont ->
                            val b = Transformer.Builder(ctx)
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        if (cont.isActive) cont.resume(null)
                                    }

                                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                        if (cont.isActive) cont.resume(exportException)
                                    }
                                })
                            plan.mime?.let { b.setVideoMimeType(it) }
                            val t = b.build()
                            cont.invokeOnCancellation { t.cancel() }
                            t.start(plan.composition, out.absolutePath)
                            scope.launch {
                                val holder = ProgressHolder()
                                while (cont.isActive) {
                                    val st = t.getProgress(holder)
                                    if (st == Transformer.PROGRESS_STATE_AVAILABLE) exportProgress = holder.progress / 100f
                                    delay(200)
                                }
                            }
                        }
                        if (error == null && saveVideoToGallery(ctx, out)) {
                            toast("Video disimpan di Movies/SP Photos")
                            exporting = false; showExport = false
                            onSaved()
                        } else {
                            out.delete()
                            toast("Ekspor gagal: ${error?.message ?: "tidak bisa menyimpan"}")
                            exporting = false
                        }
                    }
                }) { Text("Ekspor") }
            },
            dismissButton = { if (!exporting) TextButton(onClick = { showExport = false }) { Text("Batal") } }
        )
    }
}
