@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

sealed interface Overlay {
    class Grid(val title: String, val filter: (Media) -> Boolean, val album: String?) : Overlay
    class Viewer(val items: List<Media>, val index: Int) : Overlay
    class Editor(val media: Media) : Overlay
    class Slideshow(val items: List<Media>) : Overlay
    object Settings : Overlay
    object Trash : Overlay
    object Albums : Overlay
    object ForYou : Overlay
    object RecentDays : Overlay
    object MediaTypes : Overlay
}

fun shareMedia(ctx: Context, list: List<Media>) {
    if (list.isEmpty()) return
    val i = if (list.size == 1) Intent(Intent.ACTION_SEND).apply {
        type = list[0].mime
        putExtra(Intent.EXTRA_STREAM, list[0].uri)
    } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "*/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list.map { it.uri }))
    }
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Bagikan"))
}

@Composable
private fun EnterAnim(reduce: Boolean, content: @Composable () -> Unit) {
    if (reduce) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val st = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = st,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)),
        exit = ExitTransition.None
    ) { Box(Modifier.fillMaxSize()) { content() } }
}

/** Media dari URI luar MediaStore (mis. FileProvider aplikasi lain). */
fun adhocMedia(ctx: Context, uri: Uri): Media {
    var name = uri.lastPathSegment ?: "media"
    var size = 0L
    try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                size = c.getLong(1)
            }
        }
    } catch (_: Exception) {
    }
    val lower = name.lowercase()
    val mime = ctx.contentResolver.getType(uri)
        ?: if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") || lower.endsWith(".3gp")) "video/*" else "image/*"
    val now = System.currentTimeMillis()
    return Media(0L, uri, name, mime, now, now, size, 0, 0, "", 0L)
}

fun isDefaultGallery(ctx: Context): Boolean = try {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*")
    ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
} catch (_: Exception) {
    false
}

/** Memicu dialog "Buka dengan" sistem: pilih SP Photos lalu "Selalu". */
fun openAsDefault(ctx: Context, sample: Uri?) {
    try {
        val u = sample ?: Uri.parse("content://media/external/images/media/1")
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(ctx, "Tidak bisa membuka dialog pemilihan aplikasi", Toast.LENGTH_SHORT).show()
    }
}

fun openDefaultAppsSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

@Composable
fun SPPhotosRoot(
    vm: GalleryVM,
    req: LaunchRequest = LaunchRequest(),
    onPick: (List<Uri>) -> Unit = {},
    onExit: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity

    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.load() }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    // 0 = Library, 1 = Collections; Search adalah tombol terpisah
    var main by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showSeg by rememberSaveable { mutableStateOf(false) }
    var libMode by rememberSaveable { mutableIntStateOf(2) } // 0 Years, 1 Months, 2 All
    val stack = remember { mutableStateListOf<Overlay>() }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var sortAdded by rememberSaveable { mutableStateOf(false) }
    val libState = rememberLazyGridState()
    val backdropLayer = rememberGraphicsLayer()
    val backdrop = remember(backdropLayer) { BackdropState(backdropLayer) }
    val backdropOn = vm.blurOn && !vm.reduce
    var pendingDelete by remember { mutableStateOf<List<Media>?>(null) }
    var albumDialog by remember { mutableStateOf(false) }
    var newAlbumDialog by remember { mutableStateOf(false) }

    val externalMode = req.mode == LaunchRequest.VIEW || req.mode == LaunchRequest.EDIT
    val pickMode = req.mode == LaunchRequest.PICK
    var opened by remember(req) { mutableStateOf(false) }
    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty() && externalMode && opened) onExit()
    }
    fun safe(block: () -> Unit) {
        try { block() } catch (e: Exception) {
            Toast.makeText(ctx, "Item ini tidak bisa diproses dari sini", Toast.LENGTH_SHORT).show()
        }
    }
    fun endSelect() { selecting = false; selected = emptySet() }
    fun toggle(m: Media) { selected = if (m.key in selected) selected - m.key else selected + m.key }
    fun selectedMedia(): List<Media> = vm.media.filter { it.key in selected }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        vm.load()
        if (res.resultCode == Activity.RESULT_OK) {
            endSelect()
            if (stack.lastOrNull() is Overlay.Viewer) pop()
        }
    }
    fun launchRequest(pi: PendingIntent) {
        launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }
    fun trash(list: List<Media>) {
        if (Build.VERSION.SDK_INT >= 30) {
            safe { launchRequest(MediaStore.createTrashRequest(ctx.contentResolver, list.map { it.uri }, true)) }
        } else {
            var failed = false
            list.forEach { try { ctx.contentResolver.delete(it.uri, null, null) } catch (e: Exception) { failed = true } }
            if (failed) Toast.makeText(ctx, "Sebagian item tidak bisa dihapus", Toast.LENGTH_SHORT).show()
            endSelect(); if (stack.lastOrNull() is Overlay.Viewer) pop(); vm.load()
        }
    }
    fun restore(list: List<Media>) {
        if (Build.VERSION.SDK_INT >= 30)
            safe { launchRequest(MediaStore.createTrashRequest(ctx.contentResolver, list.map { it.uri }, false)) }
    }
    fun deleteForever(list: List<Media>) {
        if (Build.VERSION.SDK_INT >= 30)
            safe { launchRequest(MediaStore.createDeleteRequest(ctx.contentResolver, list.map { it.uri })) }
    }

    val onTile: (List<Media>, Int) -> Unit = { list, idx ->
        if (pickMode) {
            if (req.multiple) toggle(list[idx]) else onPick(listOf(list[idx].uri))
        } else if (selecting) toggle(list[idx]) else stack.add(Overlay.Viewer(list, idx))
    }
    val onLong: (Media) -> Unit = { m ->
        if (pickMode) { if (req.multiple) toggle(m) }
        else if (!selecting) { selecting = true; selected = setOf(m.key) } else toggle(m)
    }
    val openGrid: (String, (Media) -> Boolean) -> Unit = { t, f -> stack.add(Overlay.Grid(t, f, null)) }

    val libItems = remember(vm.media, vm.favs, filter, sortAdded, req) {
        val pool = when {
            req.mode == LaunchRequest.PICK && req.mime?.startsWith("image") == true -> vm.media.filter { !it.isVideo }
            req.mode == LaunchRequest.PICK && req.mime?.startsWith("video") == true -> vm.media.filter { it.isVideo }
            else -> vm.media
        }
        val l = when (filter) {
            1 -> pool.filter { !it.isVideo }
            2 -> pool.filter { it.isVideo }
            3 -> pool.filter { it.key in vm.favs }
            else -> pool
        }
        if (sortAdded) l.sortedByDescending { it.addedMs } else l
    }

    LaunchedEffect(req, vm.loaded) {
        if (!externalMode || opened || !vm.loaded) return@LaunchedEffect
        val u = req.uri ?: return@LaunchedEffect
        val idx = vm.media.indexOfFirst { it.uri == u }
        opened = true
        if (req.mode == LaunchRequest.EDIT) {
            stack.add(Overlay.Editor(if (idx >= 0) vm.media[idx] else adhocMedia(ctx, u)))
        } else if (idx >= 0) {
            stack.add(Overlay.Viewer(vm.media, idx))
        } else {
            stack.add(Overlay.Viewer(listOf(adhocMedia(ctx, u)), 0))
        }
    }
    LaunchedEffect(pickMode) { if (pickMode && req.multiple) selecting = true }

    BackHandler(enabled = stack.isNotEmpty() || selecting || searching || showSeg || main != 0) {
        when {
            stack.isNotEmpty() -> pop()
            selecting -> endSelect()
            searching -> searching = false
            showSeg -> { showSeg = false; libMode = 2 }
            else -> main = 0
        }
    }

    val top = stack.lastOrNull()
    val showSel = selecting && (top == null || top is Overlay.Grid) && !pickMode
    val showNav = !selecting && top == null

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalBackdrop provides (if (backdropOn) backdrop else null)) {
        when {
            searching -> SearchScreen(vm, selecting, selected, onTile, onLong)
            main == 0 -> LibraryScreen(
                vm, libItems, libState, libMode, selecting, selected, filter, { filter = it }, sortAdded, { sortAdded = it },
                onToggleSelect = { if (selecting) endSelect() else selecting = true },
                onTile = onTile, onLong = onLong, onOpenGrid = openGrid
            )
            else -> CollectionsScreen(
                vm,
                onAlbums = { stack.add(Overlay.Albums) },
                onMemories = { stack.add(Overlay.ForYou) },
                onRecentDays = { stack.add(Overlay.RecentDays) },
                onMediaTypes = { stack.add(Overlay.MediaTypes) },
                onOpenGrid = openGrid,
                onTrash = { stack.add(Overlay.Trash) },
                onSettings = { stack.add(Overlay.Settings) }
            )
        }

        if (showNav || showSel) {
            val botH = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 118.dp
            ProgressiveBlur(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(botH),
                fade = 2, scrim = MaterialTheme.colorScheme.background
            )
        }

        if (showNav) SPBottomBar(
            main = main, searching = searching, segMode = showSeg, libMode = libMode,
            onMain = { i ->
                if (i == 0 && main == 0 && !searching) showSeg = true
                else { main = i; searching = false; showSeg = false; if (i != 0) libMode = 2 }
            },
            onSeg = { libMode = it },
            onExitSeg = { showSeg = false; libMode = 2 },
            onSearch = { searching = true; showSeg = false },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        }

        if (pickMode && req.multiple && top == null) {
            Glass(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp),
                RoundedCornerShape(50)
            ) {
                Box(
                    Modifier.clickable(enabled = selected.isNotEmpty()) { onPick(selectedMedia().map { it.uri }) }
                        .padding(horizontal = 26.dp, vertical = 13.dp)
                ) { Text("Pilih (${selected.size})", fontWeight = FontWeight.SemiBold) }
            }
        }

        stack.forEachIndexed { i, o ->
            key(i) {
                EnterAnim(vm.reduce) {
                    when (o) {
                        is Overlay.Grid -> {
                            val list = remember(vm.media, vm.favs, vm.albums) { vm.media.filter(o.filter) }
                            GridScreen(
                                vm, o.title, list, o.album, selecting, selected,
                                onBack = { if (selecting) endSelect(); pop() },
                                onToggleSelect = { if (selecting) endSelect() else selecting = true },
                                onTile = onTile, onLong = onLong
                            )
                        }
                        is Overlay.Viewer -> ViewerScreen(
                            vm, o.items, o.index,
                            onClose = { pop() },
                            onEdit = { stack.add(Overlay.Editor(it)) },
                            onDelete = { pendingDelete = listOf(it) },
                            onShare = { shareMedia(ctx, listOf(it)) }
                        )
                        is Overlay.Editor ->
                            if (o.media.isVideo) VideoEditorScreen(vm, o.media, onClose = { pop() }, onSaved = { vm.load(); pop() })
                            else EditorScreen(vm, o.media, onClose = { pop() }, onSaved = { vm.load(); pop() })
                        is Overlay.Slideshow -> SlideshowScreen(o.items) { pop() }
                        Overlay.Settings -> SettingsScreen(vm) { pop() }
                        Overlay.Trash -> TrashScreen(vm, { pop() }, ::restore, ::deleteForever)
                        Overlay.Albums -> PageScaffold({ pop() }) {
                            AlbumsScreen(
                                vm, rememberLazyGridState(),
                                onOpen = { t, f, a -> stack.add(Overlay.Grid(t, f, a)) },
                                onTrash = { stack.add(Overlay.Trash) },
                                topPad = 72.dp
                            )
                        }
                        Overlay.ForYou -> PageScaffold({ pop() }) {
                            ForYouScreen(
                                vm, rememberLazyListState(),
                                onOpen = { m -> val ids = m.items.map { it.key }.toSet(); stack.add(Overlay.Grid(m.title, { it.key in ids }, null)) },
                                onPlay = { stack.add(Overlay.Slideshow(it)) },
                                topPad = 72.dp
                            )
                        }
                        Overlay.RecentDays -> PageScaffold({ pop() }) {
                            RecentDaysScreen(vm, openGrid) { stack.add(Overlay.Slideshow(it)) }
                        }
                        Overlay.MediaTypes -> PageScaffold({ pop() }) { MediaTypesScreen(vm, openGrid) }
                    }
                }
            }
        }

        if (showSel) CompositionLocalProvider(LocalBackdrop provides (if (backdropOn && top == null) backdrop else null)) {
            val sel = selectedMedia()
            SelectionBar(
                count = sel.size,
                allFav = sel.isNotEmpty() && sel.all { it.key in vm.favs },
                onShare = { shareMedia(ctx, sel) },
                onFav = { vm.setFav(sel, !sel.all { it.key in vm.favs }) },
                onAlbum = { albumDialog = true },
                onDelete = { pendingDelete = sel },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus ${list.size} item?") },
            text = {
                Text(
                    if (Build.VERSION.SDK_INT >= 30)
                        "Item dipindahkan ke Recently Deleted dan bisa dipulihkan selama sekitar 30 hari."
                    else "Di Android 10 item dihapus permanen dan tidak bisa dipulihkan."
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; trash(list) }) { Text("Hapus", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } }
        )
    }

    if (albumDialog) AlertDialog(
        onDismissRequest = { albumDialog = false },
        title = { Text("Tambah ke album") },
        text = {
            Column {
                vm.albums.keys.forEach { name ->
                    TextButton(onClick = {
                        vm.addToAlbum(name, selected); albumDialog = false; endSelect()
                        Toast.makeText(ctx, "Ditambahkan ke $name", Toast.LENGTH_SHORT).show()
                    }) { Text(name) }
                }
                TextButton(onClick = { albumDialog = false; newAlbumDialog = true }) { Text("+ Album baru") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { albumDialog = false }) { Text("Batal") } }
    )
    if (newAlbumDialog) NameDialog("Album baru", "", "Buat", { newAlbumDialog = false }) {
        if (vm.createAlbum(it, selected)) { newAlbumDialog = false; endSelect() }
    }
}

// ---------------- Settings ----------------

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        content()
    }
}

@Composable
private fun StatRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, modifier = Modifier.weight(1f))
        Text(v, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
    }
}

@Composable
fun SettingsScreen(vm: GalleryVM, onBack: () -> Unit) {
    val photos = vm.media.filter { !it.isVideo }
    val videos = vm.media.filter { it.isVideo }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.statusBarsPadding().padding(top = 72.dp, start = 4.dp)
                )
            }
            item {
                SettingCard("Tampilan") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Sistem", "Terang", "Gelap").forEachIndexed { i, n -> GlassChip(n, vm.theme == i, { vm.updateTheme(i) }) }
                    }
                    Text("Intensitas kaca")
                    Slider(value = vm.glass, onValueChange = { vm.updateGlass(it) }, valueRange = 0f..1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Kurangi efek")
                            Text(
                                "Kaca jadi solid dan animasi transisi dimatikan (hemat baterai / Reduce Motion).",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        Switch(checked = vm.reduce, onCheckedChange = { vm.updateReduce(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Blur latar")
                            Text(
                                "Memburamkan foto di belakang bilah kaca. Matikan jika aplikasi terasa berat atau sering keluar sendiri.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        Switch(checked = vm.blurOn, onCheckedChange = { vm.updateBlur(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Blur saat scroll")
                            Text(
                                "Matikan (disarankan) agar scroll mulus di 90/120 Hz: blur dijeda saat menggulir dan kembali setelah berhenti.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        Switch(checked = vm.blurScroll, onCheckedChange = { vm.updateBlurScroll(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pembiasan lensa (eksperimental)")
                            Text(
                                "Lensa membiaskan & memperbesar isi di bawahnya (Android 13+). Jika bermasalah, matikan.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        Switch(checked = vm.refractOn, onCheckedChange = { vm.updateRefract(it) })
                    }
                }
            }
            item {
                SettingCard("Kepadatan grid (juga bisa pinch di grid)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (3..8).forEach { c -> GlassChip("$c", vm.columns == c, { vm.updateColumns(c) }) }
                    }
                }
            }
            item {
                SettingCard("Penyimpanan") {
                    StatRow("Foto", "${photos.size} · ${formatBytes(photos.sumOf { it.size })}")
                    StatRow("Video", "${videos.size} · ${formatBytes(videos.sumOf { it.size })}")
                    StatRow("Total", formatBytes(vm.media.sumOf { it.size }))
                    StatRow("Recently Deleted", "${vm.trashed.size}")
                }
            }
            item {
                val sctx = LocalContext.current
                var isDefault by remember { mutableStateOf(isDefaultGallery(sctx)) }
                SettingCard("Galeri default") {
                    StatRow("Status", if (isDefault) "SP Photos ✓" else "Belum jadi default")
                    Text(
                        "Agar foto & video dari file manager, chat, dan kamera terbuka di SP Photos: tekan \"Jadikan default\", " +
                            "pilih SP Photos lalu \"Selalu\". Jika yang terbuka aplikasi galeri lain, buka \"Pengaturan\" → " +
                            "Aplikasi default / Kelola aplikasi → galeri lama → hapus pengaturan default, lalu ulangi.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip("Jadikan default", false, { openAsDefault(sctx, vm.media.firstOrNull { !it.isVideo }?.uri) })
                        GlassChip("Pengaturan", false, { openDefaultAppsSettings(sctx) })
                        GlassChip("Periksa", false, { isDefault = isDefaultGallery(sctx) })
                    }
                }
            }
            item {
                SettingCard("Privasi") {
                    Text(
                        "Foto & video diproses di perangkat. Aplikasi tidak memakai pengenalan wajah dan tidak membaca lokasi foto. " +
                            "Internet hanya dipakai jika kamu mengunduh font atau stiker tambahan (bisa dipakai offline setelah diunduh). " +
                            "Pengenalan teks pada foto berjalan di perangkat. Favorit dan album kustom disimpan lokal.",
                        fontSize = 14.sp
                    )
                }
            }
            item {
                SettingCard("Tentang") {
                    StatRow("SP Photos", "v1.7")
                    Text(
                        "Konsep UI mengacu pada dokumen “iOS 27 Photos · Liquid Glass”. Bukan produk atau tampilan resmi Apple.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }
        }
        GlassIconButton(
            Icons.Rounded.ArrowBack, "Kembali", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), size = 42
        )
    }
}
