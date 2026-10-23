@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(top = 10.dp, bottom = 4.dp))
}

private fun dim() = 0.6f

// ---------------- For You ----------------

@Composable
fun ForYouScreen(
    vm: GalleryVM,
    state: LazyListState,
    onOpen: (Memory) -> Unit,
    onPlay: (List<Media>) -> Unit,
    topPad: Dp = 20.dp
) {
    val memories = remember(vm.media) { buildMemories(vm.media) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = topPad, start = 4.dp)) {
                Text("For You", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("Memories & kurasi", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(dim()))
            }
        }
        if (memories.isEmpty()) {
            item {
                Text(
                    if (vm.loaded) "Memories muncul saat satu bulan punya minimal 5 foto/video."
                    else "Memuat…",
                    modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onBackground.copy(dim())
                )
            }
        }
        items(memories, key = { it.title }) { m ->
            val cover = m.items[m.items.size / 2]
            Box(
                Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(32.dp))
                    .clickable { onOpen(m) }
            ) {
                Thumb(cover, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f)))
                    )
                )
                Glass(Modifier.align(Alignment.BottomStart).padding(14.dp), RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(m.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(m.subtitle, fontSize = 12.sp)
                    }
                }
                GlassIconButton(
                    Icons.Rounded.PlayArrow, "Putar slideshow", { onPlay(m.items) },
                    Modifier.align(Alignment.BottomEnd).padding(14.dp), size = 48
                )
            }
        }
    }
}

// ---------------- Albums ----------------

@Composable
private fun AlbumCard(title: String, count: Int, cover: Media?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp))
                .background(Color(0x33888888)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) Thumb(cover, Modifier.fillMaxSize())
            else Icon(Icons.Rounded.Collections, null, tint = Color(0x88888888), modifier = Modifier.size(36.dp))
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text("$count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(dim()))
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun AlbumsScreen(
    vm: GalleryVM,
    state: LazyGridState,
    onOpen: (String, (Media) -> Boolean, String?) -> Unit,
    onTrash: () -> Unit,
    topPad: Dp = 20.dp
) {
    val buckets = remember(vm.media) {
        vm.media.groupBy { it.bucket }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Media>>> { it.key == "Camera" }.thenByDescending { it.value.size })
    }
    var creating by remember { mutableStateOf(false) }
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = full) {
            Row(
                Modifier.statusBarsPadding().padding(top = topPad, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Albums", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                GlassIconButton(Icons.Rounded.Add, "Buat album", { creating = true }, size = 42)
            }
        }
        if (vm.albums.isNotEmpty()) {
            item(span = full) { SectionTitle("My Albums") }
            items(vm.albums.keys.toList(), key = { "u$it" }) { name ->
                val keys = vm.albums[name] ?: emptySet()
                val inAlbum = vm.media.filter { it.key in keys }
                AlbumCard(name, inAlbum.size, inAlbum.firstOrNull()) {
                    onOpen(name, { it.key in (vm.albums[name] ?: emptySet()) }, name)
                }
            }
        }
        item(span = full) { SectionTitle("Di perangkat") }
        items(buckets, key = { "b${it.key}" }) { e ->
            AlbumCard(e.key, e.value.size, e.value.firstOrNull()) {
                val b = e.key
                onOpen(b, { it.bucket == b }, null)
            }
        }
        item(span = full) { SectionTitle("Utilities") }
        item(key = "u-fav") {
            val f = vm.media.filter { it.key in vm.favs }
            AlbumCard("Favorites", f.size, f.firstOrNull()) { onOpen("Favorites", { it.key in vm.favs }, null) }
        }
        item(key = "u-vid") {
            val v = vm.media.filter { it.isVideo }
            AlbumCard("Videos", v.size, v.firstOrNull()) { onOpen("Videos", { it.isVideo }, null) }
        }
        item(key = "u-del") {
            AlbumCard("Recently Deleted", vm.trashed.size, vm.trashed.firstOrNull(), onTrash)
        }
    }
    if (creating) NameDialog("Album baru", "", "Buat", { creating = false }) {
        if (vm.createAlbum(it)) creating = false
    }
}

// ---------------- Grid generik (album / koleksi / memory) ----------------

@Composable
fun GridScreen(
    vm: GalleryVM,
    title: String,
    items: List<Media>,
    albumName: String?,
    selecting: Boolean,
    selected: Set<Long>,
    onBack: () -> Unit,
    onToggleSelect: () -> Unit,
    onTile: (List<Media>, Int) -> Unit,
    onLong: (Media) -> Unit
) {
    val state = rememberLazyGridState()
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .pinchZoom { fewer -> vm.updateColumns(vm.columns + if (fewer) -1 else 1) }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(vm.columns), state = state, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(top = 72.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${items.size} item", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(dim()))
                }
            }
            itemsIndexed(items, key = { _, m -> "g${m.key}" }) { i, m ->
                PhotoTile(m, m.key in vm.favs, selecting, m.key in selected,
                    onClick = { onTile(items, i) }, onLong = { onLong(m) })
            }
        }
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassIconButton(Icons.Rounded.ArrowBack, "Kembali", onBack, size = 42)
            Spacer(Modifier.weight(1f))
            GlassChip(if (selecting) "Done" else "Select", selecting, onToggleSelect)
            if (albumName != null) Box {
                GlassIconButton(Icons.Rounded.MoreHoriz, "Menu album", { menu = true }, size = 42)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Ubah nama") }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Hapus album") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        if (items.isEmpty()) {
            Text(
                "Album kosong. Pilih foto di Library lalu tekan “Album”.",
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(dim())
            )
        }
    }
    if (rename && albumName != null) NameDialog("Ubah nama album", albumName, "Simpan", { rename = false }) {
        if (vm.renameAlbum(albumName, it)) { rename = false; onBack() }
    }
    if (confirmDelete && albumName != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Hapus album “$albumName”?") },
        text = { Text("Hanya album-nya yang dihapus. Foto dan video tetap ada di perangkat.") },
        confirmButton = { TextButton(onClick = { vm.deleteAlbum(albumName); confirmDelete = false; onBack() }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
    )
}

// ---------------- Search ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    vm: GalleryVM,
    selecting: Boolean,
    selected: Set<Long>,
    onTile: (List<Media>, Int) -> Unit,
    onLong: (Media) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val tokens = remember(query) { query.lowercase().split(" ").filter { it.isNotBlank() } }
    val active = tokens.isNotEmpty() || type != 0
    val results = remember(vm.media, vm.favs, tokens, type) {
        if (!active) emptyList() else vm.media.filter {
            val fav = it.key in vm.favs
            val typeOk = when (type) { 1 -> !it.isVideo; 2 -> it.isVideo; 3 -> fav; else -> true }
            typeOk && matchesQuery(it, tokens, fav, today)
        }
    }
    val suggestions = remember(vm.media) {
        val months = vm.media.map { formatDate(it.dateMs, "MMMM yyyy") }.distinct().take(3)
        val albums = vm.media.groupBy { it.bucket }.entries.sortedByDescending { it.value.size }.take(5).map { it.key }
        listOf("Video", "Screenshot", "Favorit", "Kemarin", "Hari ini") + months + albums
    }
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    BackdropScrollPause({ gridState.isScrollInProgress }, vm.blurScroll)
    LazyVerticalGrid(
        state = gridState, columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().backdropSource(),
        contentPadding = PaddingValues(bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item(span = full) {
            Column(Modifier.statusBarsPadding().padding(top = 20.dp, start = 16.dp, end = 16.dp)) {
                Text("Search", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.height(10.dp))
                Glass(Modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(27.dp), blur = false) {
                    TextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxSize(),
                        singleLine = true,
                        placeholder = { Text("Cari: pantai, Oktober, video, screenshot…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) Icon(
                                Icons.Rounded.Close, "Hapus", Modifier.clickable { query = "" }
                            )
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.addRecent(query); focus.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Semua", "Foto", "Video", "Favorit").forEachIndexed { i, n ->
                        GlassChip(n, type == i, { type = i })
                    }
                }
            }
        }
        if (!active) {
            if (vm.recents.isNotEmpty()) {
                item(span = full) {
                    Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent searches", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearRecents() }) { Text("Hapus") }
                    }
                }
                item(span = full) {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { vm.recents.forEach { r -> GlassChip(r, false, { query = r }) } }
                }
            }
            item(span = full) {
                Text("Suggestions", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp))
            }
            item(span = full) {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { suggestions.forEach { s -> GlassChip(s, false, { query = s; vm.addRecent(s) }) } }
            }
        } else {
            item(span = full) {
                Text(
                    "${results.size} hasil", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(dim()),
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                )
            }
            itemsIndexed(results, key = { _, m -> "s${m.key}" }) { i, m ->
                PhotoTile(m, m.key in vm.favs, selecting, m.key in selected,
                    onClick = { onTile(results, i) }, onLong = { onLong(m) })
            }
        }
    }
}

// ---------------- Recently Deleted ----------------

@Composable
fun TrashScreen(
    vm: GalleryVM,
    onBack: () -> Unit,
    onRestore: (List<Media>) -> Unit,
    onDeleteForever: (List<Media>) -> Unit
) {
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val items = vm.trashed
    val target = if (sel.isEmpty()) items else items.filter { it.key in sel }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(top = 72.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                    Text("Recently Deleted", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Item dipindahkan ke tempat sampah sistem dan dihapus otomatis setelah sekitar 30 hari.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(dim())
                    )
                }
            }
            items(items, key = { "t${it.key}" }) { m ->
                PhotoTile(m, false, true, m.key in sel,
                    onClick = { sel = if (m.key in sel) sel - m.key else sel + m.key }, onLong = {})
            }
        }
        GlassIconButton(
            Icons.Rounded.ArrowBack, "Kembali", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), size = 42
        )
        if (items.isEmpty()) {
            Text("Tidak ada item", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onBackground.copy(dim()))
        } else {
            Glass(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).fillMaxWidth(),
                RoundedCornerShape(30.dp)
            ) {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { onRestore(target) }) {
                        Icon(Icons.Rounded.Restore, null); Spacer(Modifier.size(6.dp))
                        Text(if (sel.isEmpty()) "Pulihkan semua" else "Pulihkan (${target.size})")
                    }
                    TextButton(onClick = { onDeleteForever(target) }) {
                        Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF453A)); Spacer(Modifier.size(6.dp))
                        Text(if (sel.isEmpty()) "Hapus semua" else "Hapus (${target.size})", color = Color(0xFFFF453A))
                    }
                }
            }
        }
    }
}

// ---------------- Bar seleksi ----------------

@Composable
fun SelectionBar(
    count: Int,
    allFav: Boolean,
    onShare: () -> Unit,
    onFav: () -> Unit,
    onAlbum: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Glass(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(32.dp)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (count == 0) "Pilih item" else "$count dipilih", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                val on = count > 0
                val a = if (on) 1f else 0.35f
                IconBtn(Icons.Rounded.Share, "Bagikan", on, a, onShare)
                IconBtn(if (allFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorit", on, a, onFav)
                IconBtn(Icons.Rounded.Collections, "Tambah ke album", on, a, onAlbum)
                IconBtn(Icons.Rounded.Delete, "Hapus", on, a, onDelete, Color(0xFFFF453A))
            }
        }
    }
}

@Composable
private fun IconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, alpha: Float,
    onClick: () -> Unit, tint: Color = Color.Unspecified
) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, desc, modifier = Modifier.size(24.dp),
            tint = (if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint).copy(alpha = alpha)
        )
    }
}

// ---------------- Slideshow ----------------

@Composable
fun SlideshowScreen(items: List<Media>, onClose: () -> Unit) {
    val photos = remember(items) { items.filter { !it.isVideo } }
    var idx by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    LaunchedEffect(playing, photos) {
        while (playing && photos.size > 1) {
            delay(3200)
            idx = (idx + 1) % photos.size
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { playing = !playing }) {
        if (photos.isEmpty()) {
            Text("Tidak ada foto untuk slideshow", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            Crossfade(targetState = idx, animationSpec = tween(900), label = "slide") { i ->
                AsyncImage(
                    model = photos[i.coerceIn(0, photos.lastIndex)].uri, contentDescription = null,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(Icons.Rounded.Close, "Tutup", onClose, size = 42)
            Spacer(Modifier.weight(1f))
            if (photos.isNotEmpty()) Text("${idx + 1}/${photos.size}", color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.size(10.dp))
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
        }
    }
}
