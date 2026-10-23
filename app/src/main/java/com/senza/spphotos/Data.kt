package com.senza.spphotos

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DAY_MS = 86_400_000L

data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val dateMs: Long,
    val addedMs: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucket: String,
    val durationMs: Long
) {
    val isVideo: Boolean get() = mime.startsWith("video/")
    /** Kunci unik (id foto & video bisa bentrok di MediaStore). */
    val key: Long get() = if (isVideo) -id else id
    val isScreenshot: Boolean
        get() = bucket.contains("screenshot", true) || name.startsWith("Screenshot", true)
}

object MediaRepo {
    fun load(ctx: Context, trashed: Boolean = false): List<Media> {
        val out = ArrayList<Media>()
        out += query(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, trashed)
        out += query(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, trashed)
        out.sortByDescending { it.dateMs }
        return out
    }

    private fun query(ctx: Context, base: Uri, video: Boolean, trashed: Boolean): List<Media> {
        val proj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )
        if (video) proj += MediaStore.MediaColumns.DURATION
        val res = ArrayList<Media>()
        try {
            val cursor = if (trashed) {
                if (Build.VERSION.SDK_INT < 30) return res
                val b = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                }
                ctx.contentResolver.query(base, proj.toTypedArray(), b, null)
            } else {
                ctx.contentResolver.query(base, proj.toTypedArray(), null, null, null)
            }
            cursor?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val iB = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iD = if (video) c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION) else -1
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val taken = c.getLong(iTaken)
                    val added = c.getLong(iAdded) * 1000
                    res += Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: if (video) "video/*" else "image/*",
                        dateMs = if (taken > 0) taken else added,
                        addedMs = added,
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        bucket = c.getString(iB) ?: "Lainnya",
                        durationMs = if (iD >= 0) c.getLong(iD) else 0L
                    )
                }
            }
        } catch (_: Exception) {
        }
        return res
    }
}

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("sp_photos", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> =
        (p.getStringSet("fav", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(s: Set<Long>) {
        p.edit().putStringSet("fav", s.map { it.toString() }.toSet()).apply()
    }

    fun albums(): LinkedHashMap<String, Set<Long>> {
        val m = LinkedHashMap<String, Set<Long>>()
        try {
            val o = JSONObject(p.getString("albums", "{}") ?: "{}")
            o.keys().forEach { k ->
                val a = o.getJSONArray(k)
                m[k] = (0 until a.length()).map { a.getLong(it) }.toSet()
            }
        } catch (_: Exception) {
        }
        return m
    }

    fun saveAlbums(m: Map<String, Set<Long>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) }
        p.edit().putString("albums", o.toString()).apply()
    }

    var recents: List<String>
        get() = (p.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) { p.edit().putString("recent", v.joinToString("\n")).apply() }

    var glass: Float
        get() = p.getFloat("glass", 0.6f)
        set(v) { p.edit().putFloat("glass", v).apply() }

    var reduce: Boolean
        get() = p.getBoolean("reduce", false)
        set(v) { p.edit().putBoolean("reduce", v).apply() }

    /** 0 = ikuti sistem, 1 = terang, 2 = gelap */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }

    var blur: Boolean
        get() = p.getBoolean("blur", true)
        set(v) { p.edit().putBoolean("blur", v).apply() }

    /** Pembiasan lensa (shader AGSL) — eksperimental, default mati. */
    var refract: Boolean
        get() = p.getBoolean("refract", false)
        set(v) { p.edit().putBoolean("refract", v).apply() }

    var blurScroll: Boolean
        get() = p.getBoolean("blurScroll", false)
        set(v) { p.edit().putBoolean("blurScroll", v).apply() }

    var columns: Int
        get() = p.getInt("columns", 5)
        set(v) { p.edit().putInt("columns", v.coerceIn(2, 8)).apply() }
}

class GalleryVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var media by mutableStateOf<List<Media>>(emptyList()); private set
    var trashed by mutableStateOf<List<Media>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var favs by mutableStateOf(store.favorites()); private set
    var albums by mutableStateOf<Map<String, Set<Long>>>(store.albums()); private set
    var recents by mutableStateOf(store.recents); private set

    var glass by mutableFloatStateOf(store.glass); private set
    var reduce by mutableStateOf(store.reduce); private set
    var theme by mutableIntStateOf(store.theme); private set
    var columns by mutableIntStateOf(store.columns); private set
    var blurOn by mutableStateOf(store.blur); private set
    var refractOn by mutableStateOf(store.refract); private set
    var blurScroll by mutableStateOf(store.blurScroll); private set

    fun load() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val l = withContext(Dispatchers.IO) { MediaRepo.load(app) }
            val t = withContext(Dispatchers.IO) { MediaRepo.load(app, true) }
            media = l
            trashed = t
            loaded = true
        }
    }

    fun toggleFav(m: Media) = setFav(listOf(m), m.key !in favs)

    fun setFav(list: List<Media>, value: Boolean) {
        val keys = list.map { it.key }
        favs = if (value) favs + keys else favs - keys.toSet()
        store.saveFavorites(favs)
    }

    fun createAlbum(name: String, keys: Set<Long> = emptySet()): Boolean {
        val n = name.trim()
        if (n.isEmpty() || albums.containsKey(n)) return false
        albums = albums + (n to keys)
        store.saveAlbums(albums)
        return true
    }

    fun addToAlbum(name: String, keys: Collection<Long>) {
        albums = albums + (name to ((albums[name] ?: emptySet()) + keys))
        store.saveAlbums(albums)
    }

    fun removeFromAlbum(name: String, keys: Collection<Long>) {
        albums = albums + (name to ((albums[name] ?: emptySet()) - keys.toSet()))
        store.saveAlbums(albums)
    }

    fun renameAlbum(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty() || (n != old && albums.containsKey(n))) return false
        albums = LinkedHashMap<String, Set<Long>>().also { out ->
            albums.forEach { (k, v) -> out[if (k == old) n else k] = v }
        }
        store.saveAlbums(albums)
        return true
    }

    fun deleteAlbum(name: String) {
        albums = albums - name
        store.saveAlbums(albums)
    }

    fun addRecent(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        recents = (listOf(t) + recents.filter { !it.equals(t, true) }).take(8)
        store.recents = recents
    }

    fun clearRecents() {
        recents = emptyList()
        store.recents = emptyList()
    }

    fun updateGlass(v: Float) { glass = v; store.glass = v }
    fun updateReduce(v: Boolean) { reduce = v; store.reduce = v }
    fun updateBlur(v: Boolean) { blurOn = v; store.blur = v }
    fun updateRefract(v: Boolean) { refractOn = v; store.refract = v }
    fun updateBlurScroll(v: Boolean) { blurScroll = v; store.blurScroll = v }
    fun updateTheme(v: Int) { theme = v; store.theme = v }
    fun updateColumns(v: Int) { val c = v.coerceIn(2, 8); columns = c; store.columns = c }
}

// ---------- Helper umum ----------

fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun groupLabel(ms: Long, todayStart: Long): String {
    val d = startOfDay(ms)
    return when {
        d >= todayStart -> "Today"
        d >= todayStart - DAY_MS + 3_600_000L -> "Yesterday"
        else -> SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ms))
    }
}

fun formatDate(ms: Long, pattern: String = "d MMM yyyy"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}

data class Memory(val title: String, val subtitle: String, val items: List<Media>)

fun buildMemories(media: List<Media>): List<Memory> {
    val out = ArrayList<Memory>()
    val now = Calendar.getInstance()
    val m0 = now.get(Calendar.MONTH)
    val d0 = now.get(Calendar.DAY_OF_MONTH)
    val y0 = now.get(Calendar.YEAR)
    val c = Calendar.getInstance()
    val onThisDay = media.filter {
        c.timeInMillis = it.dateMs
        c.get(Calendar.MONTH) == m0 && c.get(Calendar.DAY_OF_MONTH) == d0 && c.get(Calendar.YEAR) < y0
    }
    if (onThisDay.isNotEmpty())
        out += Memory("Hari ini di tahun lalu", "${onThisDay.size} item", onThisDay)
    val fmtKey = SimpleDateFormat("yyyy-MM", Locale.US)
    media.groupBy { fmtKey.format(Date(it.dateMs)) }.values
        .filter { it.size >= 5 }
        .take(12)
        .forEach {
            out += Memory(
                formatDate(it.first().dateMs, "MMMM yyyy"),
                "${it.size} foto & video",
                it
            )
        }
    return out
}

fun matchesQuery(m: Media, tokens: List<String>, fav: Boolean, todayStart: Long): Boolean {
    if (tokens.isEmpty()) return true
    val d = startOfDay(m.dateMs)
    val hay = buildString {
        append(m.name).append(' ').append(m.bucket).append(' ')
        append(formatDate(m.dateMs, "MMMM MMM yyyy EEEE")).append(' ')
        append(Locale.ENGLISH.let { SimpleDateFormat("MMMM EEEE", it).format(Date(m.dateMs)) }).append(' ')
        append(if (m.isVideo) "video film klip " else "foto photo gambar image ")
        if (fav) append("favorit favorite suka ")
        if (m.isScreenshot) append("screenshot tangkapan layar ")
        if (d >= todayStart) append("hari ini today ")
        if (d >= todayStart - DAY_MS + 3_600_000L && d < todayStart) append("kemarin yesterday ")
    }.lowercase()
    return tokens.all { hay.contains(it) }
}
