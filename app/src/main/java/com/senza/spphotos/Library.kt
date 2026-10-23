@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.senza.spphotos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Pinch dua jari: spread = thumbnail lebih besar (kolom berkurang). */
fun Modifier.pinchZoom(onStep: (fewerColumns: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var acc = 1f
        var multi = false
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.size >= 2) {
                multi = true
                acc *= ev.calculateZoom()
                ev.changes.forEach { it.consume() }
            }
        } while (ev.changes.any { it.pressed })
        if (multi) {
            if (acc > 1.25f) onStep(true) else if (acc < 0.8f) onStep(false)
        }
    }
}

/** "Oct 13–16, 2026" atau "Sep 28 – Oct 3, 2026". */
fun rangeLabel(oldMs: Long, newMs: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = oldMs }
    val b = Calendar.getInstance().apply { timeInMillis = newMs }
    val loc = Locale.ENGLISH
    val sameYear = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    val sameMonth = sameYear && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
    val sameDay = sameMonth && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH)
    fun f(p: String, ms: Long) = SimpleDateFormat(p, loc).format(Date(ms))
    return when {
        sameDay -> f("MMM d, yyyy", newMs)
        sameMonth -> "${f("MMM d", oldMs)}–${f("d, yyyy", newMs)}"
        sameYear -> "${f("MMM d", oldMs)} – ${f("MMM d, yyyy", newMs)}"
        else -> "${f("MMM d, yyyy", oldMs)} – ${f("MMM d, yyyy", newMs)}"
    }
}

@Composable
fun headerTopPadding() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 104.dp

@Composable
private fun PeriodScreen(vm: GalleryVM, byYear: Boolean, onOpen: (String, (Media) -> Boolean) -> Unit) {
    val groups = remember(vm.media, byYear) {
        val f = SimpleDateFormat(if (byYear) "yyyy" else "yyyy-MM", Locale.US)
        vm.media.groupBy { f.format(Date(it.dateMs)) }.entries.toList()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp, top = headerTopPadding(), bottom = 150.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.key }) { e ->
            val list = e.value
            val title = if (byYear) e.key else formatDate(list.first().dateMs, "MMMM yyyy")
            Box(
                Modifier.fillMaxWidth().height(if (byYear) 280.dp else 200.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x33888888))
                    .clickable {
                        val ids = list.map { it.key }.toSet()
                        onOpen(title) { it.key in ids }
                    }
            ) {
                Thumb(list[list.size / 2], Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.6f)))
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text(title, color = Color.White, fontSize = if (byYear) 34.sp else 24.sp, fontWeight = FontWeight.Bold)
                    Text("${list.size} item", color = Color.White.copy(0.85f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    vm: GalleryVM,
    items: List<Media>,
    state: LazyGridState,
    mode: Int,
    selecting: Boolean,
    selected: Set<Long>,
    filter: Int,
    onFilter: (Int) -> Unit,
    sortAdded: Boolean,
    onSort: (Boolean) -> Unit,
    onToggleSelect: () -> Unit,
    onTile: (List<Media>, Int) -> Unit,
    onLong: (Media) -> Unit,
    onOpenGrid: (String, (Media) -> Boolean) -> Unit
) {
    val cols = vm.columns
    val bg = MaterialTheme.colorScheme.background
    var menu by remember { mutableStateOf(false) }
    // Subtitle hanya dihitung ulang saat baris pertama berganti (bukan tiap frame scroll)
    val firstIdx by remember { derivedStateOf { state.firstVisibleItemIndex } }
    val atTop by remember { derivedStateOf { state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset < 40 } }
    val subtitle = remember(items, firstIdx, atTop, cols) {
        val total = "${NumberFormat.getInstance().format(items.size)} Items"
        if (atTop || items.isEmpty()) total
        else {
            val a = items.getOrNull(firstIdx)
            val b = items.getOrNull(minOf(firstIdx + cols * 8, items.lastIndex))
            if (a == null || b == null) total
            else rangeLabel(minOf(a.dateMs, b.dateMs), maxOf(a.dateMs, b.dateMs))
        }
    }
    BackdropScrollPause({ state.isScrollInProgress }, vm.blurScroll)

    Box(Modifier.fillMaxSize()) {
        if (mode == 2) {
            Box(Modifier.fillMaxSize().backdropSource().pinchZoom { fewer -> vm.updateColumns(vm.columns + if (fewer) -1 else 1) }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = headerTopPadding(), bottom = 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (!vm.loaded) {
                        items(count = 30) { Box(Modifier.aspectRatio(1f).background(Color(0x22888888))) }
                    } else if (items.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(56.dp), tint = Color(0x88888888))
                                Spacer(Modifier.height(12.dp))
                                Text("Belum ada foto atau video", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    itemsIndexed(items, key = { _, m -> "m${m.key}" }, contentType = { _, _ -> 0 }) { i, m ->
                        PhotoTile(
                            m, m.key in vm.favs, selecting, m.key in selected,
                            onClick = { onTile(items, i) },
                            onLong = { onLong(m) }
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize().backdropSource()) {
                PeriodScreen(vm, byYear = mode == 0, onOpen = onOpenGrid)
            }
        }

        // Header "Library" melayang di atas foto
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            // bilah atas ala iOS: blur progresif (kuat di tepi atas, memudar ke bawah) + gradasi gelap
            ProgressiveBlur(Modifier.matchParentSize(), fade = 1, scrim = bg)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Library", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (mode == 2) subtitle else if (mode == 0) "Years" else "Months",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Box {
                    GlassIconButton(Icons.Rounded.FilterList, "Sort & filter", { menu = true }, size = 48)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (!sortAdded) "✓ Urut: tanggal diambil" else "Urut: tanggal diambil") },
                            onClick = { onSort(false); menu = false })
                        DropdownMenuItem(
                            text = { Text(if (sortAdded) "✓ Urut: tanggal ditambahkan" else "Urut: tanggal ditambahkan") },
                            onClick = { onSort(true); menu = false })
                        listOf("Semua", "Foto", "Video", "Favorit").forEachIndexed { i, n ->
                            DropdownMenuItem(
                                text = { Text(if (filter == i) "✓ Filter: $n" else "Filter: $n") },
                                onClick = { onFilter(i); menu = false })
                        }
                    }
                }
                Glass(Modifier.height(48.dp), RoundedCornerShape(50)) {
                    Box(
                        Modifier.clickable(onClick = onToggleSelect).padding(horizontal = 24.dp).height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (selecting) "Done" else "Select", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
