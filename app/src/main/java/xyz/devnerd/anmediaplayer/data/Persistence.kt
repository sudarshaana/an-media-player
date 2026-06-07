package xyz.devnerd.anmediaplayer.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import xyz.devnerd.anmediaplayer.data.source.HttpMediaSource

private val Context.repoStore: DataStore<Preferences> by preferencesDataStore(name = "repo")

/** key = "server|path/file" */
fun progressKey(server: String, path: List<String>, file: String): String =
    "$server|${(path + file).joinToString("/")}"

data class ProgressRecord(
    val server: String,
    val path: List<String>,
    val file: String,
    val pos: Int,
    val dur: Int,
    val updated: Long,
    val cover: String? = null,
)

/**
 * App-wide persisted state: saved servers, bookmarks, playback progress.
 * Snapshot-backed so Compose recomposes; writes flush to DataStore.
 * Replaces the old in-memory fake seeds.
 */
object AppRepo {
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_SERVERS = stringPreferencesKey("servers")
    private val KEY_BOOKMARKS = stringPreferencesKey("bookmarks")
    private val KEY_PROGRESS = stringPreferencesKey("progress")

    val servers = mutableStateListOf<Server>()
    val bookmarks = mutableStateListOf<Bookmark>()
    val progress = mutableStateMapOf<String, ProgressRecord>()

    fun init(context: Context) {
        ctx = context.applicationContext
        // initial synchronous load so first frame has data
        runBlocking {
            val prefs = ctx.repoStore.data.first()
            servers.addAll(parseServers(prefs[KEY_SERVERS]))
            bookmarks.addAll(parseBookmarks(prefs[KEY_BOOKMARKS]))
            progress.putAll(parseProgress(prefs[KEY_PROGRESS]))
        }
    }

    // ── servers ──
    fun addServer(s: Server) { servers.removeAll { it.id == s.id }; servers.add(s); persistServers() }
    fun removeServer(id: String) { servers.removeAll { it.id == id }; persistServers() }
    fun toggleFavorite(id: String) {
        val i = servers.indexOfFirst { it.id == id }
        if (i >= 0) { servers[i] = servers[i].copy(favorite = !servers[i].favorite); persistServers() }
    }
    fun serverById(id: String) = servers.firstOrNull { it.id == id }

    private fun persistServers() {
        val snapshot = servers.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_SERVERS] = serializeServers(snapshot) } }
    }

    // ── bookmarks ──
    fun isBookmarked(server: String, path: List<String>) = bookmarks.any { it.server == server && it.path == path }
    fun toggleBookmark(server: String, path: List<String>) {
        val i = bookmarks.indexOfFirst { it.server == server && it.path == path }
        if (i >= 0) bookmarks.removeAt(i) else bookmarks.add(Bookmark(server, path))
        val snapshot = bookmarks.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_BOOKMARKS] = serializeBookmarks(snapshot) } }
    }

    // ── progress ──
    fun getProgress(key: String): Int = progress[key]?.pos ?: 0
    fun isWatched(key: String, dur: Int?): Boolean {
        if (dur == null || dur == 0) return false
        val p = progress[key] ?: return false
        return p.pos.toFloat() / dur >= 0.92f
    }
    fun saveProgress(server: String, path: List<String>, file: String, pos: Int, dur: Int, nowMs: Long, cover: String? = null) {
        val key = progressKey(server, path, file)
        val keepCover = cover ?: progress[key]?.cover
        progress[key] = ProgressRecord(server, path, file, pos, dur, nowMs, keepCover)
        val snapshot = progress.values.toList()
        scope.launch { ctx.repoStore.edit { it[KEY_PROGRESS] = serializeProgress(snapshot) } }
    }

    /** In-progress items for the Continue-watching shelf, newest first. */
    fun continueItems(): List<ContinueItem> = progress.values
        .filter { it.dur > 0 && it.pos > 5 && it.pos.toFloat() / it.dur < 0.96f }
        .sortedByDescending { it.updated }
        .map {
            val np = prettyName(it.file)
            ContinueItem(it.server, it.path, it.file, it.pos, it.dur, np.primary, np.secondary, if (np.ep != null) "episode" else "movie", it.cover)
        }

    // ── (de)serialization ──
    private fun serializeServers(list: List<Server>) = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("url", s.url); put("protocol", s.protocol)
                put("auth", s.auth); put("user", s.user ?: JSONObject.NULL); put("password", s.password ?: JSONObject.NULL)
                put("parser", s.parser); put("lastUsed", s.lastUsed); put("favorite", s.favorite)
            })
        }
    }.toString()

    private fun parseServers(raw: String?): List<Server> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Server(
                            id = o.getString("id"), name = o.getString("name"), url = o.getString("url"),
                            protocol = o.optString("protocol", "HTTP"), auth = o.optBoolean("auth", false),
                            user = o.optString("user").ifBlank { null }, password = o.optString("password").ifBlank { null },
                            parser = o.optString("parser", "auto"), lastUsed = o.optString("lastUsed", ""),
                            favorite = o.optBoolean("favorite", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeBookmarks(list: List<Bookmark>) = JSONArray().apply {
        list.forEach { put(JSONObject().apply { put("server", it.server); put("path", JSONArray(it.path)) }) }
    }.toString()

    private fun parseBookmarks(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("path")
                    add(Bookmark(o.getString("server"), List(p.length()) { p.getString(it) }))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeProgress(list: List<ProgressRecord>) = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("server", r.server); put("path", JSONArray(r.path)); put("file", r.file)
                put("pos", r.pos); put("dur", r.dur); put("updated", r.updated); put("cover", r.cover ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun parseProgress(raw: String?): Map<String, ProgressRecord> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = o.getJSONArray("path")
                    val path = List(p.length()) { p.getString(it) }
                    val r = ProgressRecord(o.getString("server"), path, o.getString("file"), o.getInt("pos"), o.getInt("dur"), o.optLong("updated"), o.optString("cover").ifBlank { null })
                    put(progressKey(r.server, r.path, r.file), r)
                }
            }
        }.getOrDefault(emptyMap())
    }
}

/** Listing facade over the HTTP source, resolving servers from AppRepo. */
object MediaRepo {
    private val http = HttpMediaSource()

    suspend fun list(serverId: String, path: List<String>): List<Entry> {
        val s = AppRepo.serverById(serverId) ?: error("Unknown server")
        return http.list(s, path)
    }

    fun fileUrl(serverId: String, path: List<String>, file: String): String {
        val s = AppRepo.serverById(serverId) ?: return ""
        return http.fileUrl(s, path, file)
    }

    /** URL of the first image inside a folder (poster/cover), or null. */
    suspend fun firstImageUrl(serverId: String, path: List<String>): String? {
        val s = AppRepo.serverById(serverId) ?: return null
        val entries = runCatching { http.list(s, path) }.getOrDefault(emptyList())
        val img = entries.firstOrNull { it.type == EntryType.IMAGE } ?: return null
        return http.fileUrl(s, path, img.name)
    }
}

/** Lazy cache of folder cover-image URLs (snapshot-backed for Compose). */
object ThumbCache {
    private val cache = mutableStateMapOf<String, String?>()
    private val inflight = mutableSetOf<String>()

    fun key(serverId: String, path: List<String>) = "$serverId|${path.joinToString("/")}"
    fun cached(key: String): String? = cache[key]
    fun has(key: String): Boolean = cache.containsKey(key)

    suspend fun resolve(serverId: String, path: List<String>) {
        val k = key(serverId, path)
        if (cache.containsKey(k) || k in inflight) return
        inflight.add(k)
        cache[k] = runCatching { MediaRepo.firstImageUrl(serverId, path) }.getOrNull()
        inflight.remove(k)
    }
}

/** Real downloads via Android DownloadManager → app external Movies dir. Persisted. */
object DownloadsStore {
    val items = mutableStateListOf<Download>()
    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val KEY_DOWNLOADS = stringPreferencesKey("downloads")

    fun init(context: Context) {
        appCtx = context.applicationContext
        runBlocking { items.addAll(parseDownloads(appCtx.repoStore.data.first()[KEY_DOWNLOADS])) }
    }

    private fun persist() {
        if (!::appCtx.isInitialized) return
        val snap = items.toList()
        scope.launch { appCtx.repoStore.edit { it[KEY_DOWNLOADS] = serializeDownloads(snap) } }
    }

    private fun dm(ctx: Context) = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager

    fun enqueue(ctx: Context, server: String, path: List<String>, file: String, title: String, sub: String, size: Long, durSec: Int, url: String, wifiOnly: Boolean) {
        if (url.isBlank()) return
        if (items.any { it.file == file && it.state != DownloadState.FAILED }) return
        val safe = file.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle(title)
            .setDescription(sub)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, android.os.Environment.DIRECTORY_MOVIES, safe)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val id = runCatching { dm(ctx).enqueue(req) }.getOrNull() ?: return
        items.add(Download("dl_$id", title, sub, file, size, DownloadState.DOWNLOADING, 0, null, server, path, durSec, id))
        persist()
    }

    /** Poll DownloadManager and update item progress/state/localUri. */
    fun refresh(ctx: Context) {
        val active = items.filter { it.dmId != null && (it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED) }
        if (active.isEmpty()) return
        val manager = dm(ctx)
        for (d in active) {
            val q = android.app.DownloadManager.Query().setFilterById(d.dmId!!)
            runCatching {
                manager.query(q).use { c ->
                    if (!c.moveToFirst()) { update(d.id, DownloadState.FAILED); return@use }
                    val status = c.getInt(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    when (status) {
                        android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                            val uri = c.getString(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI))
                            update(d.id, DownloadState.DONE, 100, "Just now", uri)
                        }
                        android.app.DownloadManager.STATUS_FAILED -> update(d.id, DownloadState.FAILED)
                        android.app.DownloadManager.STATUS_PENDING, android.app.DownloadManager.STATUS_PAUSED -> update(d.id, DownloadState.QUEUED, pct)
                        else -> update(d.id, DownloadState.DOWNLOADING, pct)
                    }
                }
            }
        }
    }

    private fun update(id: String, state: DownloadState, progress: Int? = null, whenLabel: String? = null, localUri: String? = null) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) {
            val prev = items[i]
            items[i] = prev.copy(state = state, progress = progress ?: prev.progress, whenLabel = whenLabel ?: prev.whenLabel, localUri = localUri ?: prev.localUri)
            if (prev.state != state || prev.localUri != items[i].localUri) persist()
        }
    }

    fun remove(ctx: Context, id: String) {
        items.firstOrNull { it.id == id }?.dmId?.let { runCatching { dm(ctx).remove(it) } }
        items.removeAll { it.id == id }
        persist()
    }

    private fun serializeDownloads(list: List<Download>): String = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().apply {
                put("id", d.id); put("title", d.title); put("sub", d.sub); put("file", d.file)
                put("size", d.size); put("state", d.state.name); put("progress", d.progress ?: JSONObject.NULL)
                put("whenLabel", d.whenLabel ?: JSONObject.NULL); put("server", d.server); put("path", JSONArray(d.path))
                put("durSec", d.durSec); put("dmId", d.dmId ?: JSONObject.NULL); put("localUri", d.localUri ?: JSONObject.NULL)
            })
        }
    }.toString()

    private fun parseDownloads(s: String?): List<Download> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pathArr = o.getJSONArray("path")
                val path = (0 until pathArr.length()).map { pathArr.getString(it) }
                Download(
                    o.getString("id"), o.getString("title"), o.getString("sub"), o.getString("file"),
                    o.getLong("size"), DownloadState.valueOf(o.getString("state")),
                    if (o.isNull("progress")) null else o.getInt("progress"),
                    o.optString("whenLabel").ifBlank { null }, o.getString("server"), path, o.getInt("durSec"),
                    if (o.isNull("dmId")) null else o.getLong("dmId"),
                    o.optString("localUri").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }
}
