@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Kerangka halaman penuh dengan tombol kembali (untuk sub-halaman Collections). */
@Composable
fun PageScaffold(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content()
        GlassIconButton(
            Icons.Rounded.ArrowBack, "Kembali", onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp), size = 42
        )
    }
}

@Composable
private fun RoundChevron(icon: ImageVector = Icons.Rounded.ChevronRight, rotation: Float = 0f) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp).rotate(rotation)) }
}

@Composable
private fun CollectionRow(title: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RoundChevron()
        }
    }
}

@Composable
private fun UtilityChip(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(0.7f))
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 15.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
fun CollectionsScreen(
    vm: GalleryVM,
    onAlbums: () -> Unit,
    onMemories: () -> Unit,
    onRecentDays: () -> Unit,
    onMediaTypes: () -> Unit,
    onOpenGrid: (String, (Media) -> Boolean) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit
) {
    var utilOpen by rememberSaveable { mutableStateOf(true) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    BackdropScrollPause({ listState.isScrollInProgress }, vm.blurScroll)
    var menu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().backdropSource(), state = listState, contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Collections", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box {
                    GlassIconButton(Icons.Rounded.MoreHoriz, "Menu", { menu = true }, size = 48, blur = false)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Recently Deleted") }, onClick = { menu = false; onTrash() })
                    }
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF5E5CE6), Color(0xFF64D2FF))))
                        .clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center
                ) { Text("SP", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
        item { CollectionRow("Albums", onAlbums) }
        item { CollectionRow("Memories", onMemories) }
        item { CollectionRow("Recent Days", onRecentDays) }
        item { CollectionRow("Media Types", onMediaTypes) }
        item {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
                Row(
                    Modifier.fillMaxWidth().clickable { utilOpen = !utilOpen }
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Utilities", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        Icons.Rounded.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    RoundChevron(Icons.Rounded.KeyboardArrowDown, if (utilOpen) 0f else -90f)
                }
            }
        }
        if (utilOpen) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UtilityChip(Icons.Rounded.FavoriteBorder, "Favorites", Modifier.weight(1f)) {
                            onOpenGrid("Favorites") { it.key in vm.favs }
                        }
                        UtilityChip(Icons.Rounded.Videocam, "Videos", Modifier.weight(1f)) {
                            onOpenGrid("Videos") { it.isVideo }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UtilityChip(Icons.Rounded.Smartphone, "Screenshots", Modifier.weight(1f)) {
                            onOpenGrid("Screenshots") { it.isScreenshot }
                        }
                        UtilityChip(Icons.Rounded.Delete, "Recently Deleted", Modifier.weight(1f), onTrash)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentDaysScreen(
    vm: GalleryVM,
    onOpen: (String, (Media) -> Boolean) -> Unit,
    onPlay: (List<Media>) -> Unit
) {
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val days = remember(vm.media) {
        vm.media.groupBy { startOfDay(it.dateMs) }.entries.sortedByDescending { it.key }.take(30)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 76.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Recent Days", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.statusBarsPadding().padding(start = 6.dp, bottom = 4.dp)
            )
        }
        items(days, key = { it.key }) { (day, list) ->
            val title = when {
                day >= today -> "Today"
                day >= today - DAY_MS + 3_600_000L -> "Yesterday"
                else -> formatDate(day, "EEEE, d MMMM")
            }
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(28.dp))
                    .background(Color(0x33888888))
                    .clickable { val ids = list.map { it.key }.toSet(); onOpen(title) { it.key in ids } }
            ) {
                Thumb(list.first(), Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("${list.size} item", color = Color.White.copy(0.85f), fontSize = 13.sp)
                }
                GlassIconButton(
                    Icons.Rounded.PlayArrow, "Putar slideshow", { onPlay(list) },
                    Modifier.align(Alignment.BottomEnd).padding(14.dp), size = 46
                )
            }
        }
    }
}

@Composable
fun MediaTypesScreen(vm: GalleryVM, onOpen: (String, (Media) -> Boolean) -> Unit) {
    val types: List<Pair<String, (Media) -> Boolean>> = listOf(
        "Photos" to { m: Media -> !m.isVideo },
        "Videos" to { m: Media -> m.isVideo },
        "Screenshots" to { m: Media -> m.isScreenshot }
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 76.dp, bottom = 40.dp)) {
        item {
            Text(
                "Media Types", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, bottom = 12.dp)
            )
        }
        items(types, key = { it.first }) { (name, pred) ->
            val count = vm.media.count(pred)
            Column(Modifier.fillMaxWidth().clickable { onOpen(name, pred) }) {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.onBackground.copy(0.16f)))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("$count", color = MaterialTheme.colorScheme.onBackground.copy(0.6f), modifier = Modifier.padding(end = 12.dp))
                    RoundChevron()
                }
            }
        }
    }
}
