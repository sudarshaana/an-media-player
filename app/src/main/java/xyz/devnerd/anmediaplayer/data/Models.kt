package xyz.devnerd.anmediaplayer.data

/** Entry type drives the leading tile/icon. */
enum class EntryType { DIR, VIDEO, SUBTITLE, IMAGE, OTHER }

/** A normalized listing entry — every parser maps onto this. */
data class Entry(
    val name: String,
    val type: EntryType,
    val isDir: Boolean,
    val size: Long? = null,
    val mtime: String? = null,
    val durSec: Int? = null,
    val title: String? = null,
    val year: Int? = null,
)

data class Server(
    val id: String,
    val name: String,
    val url: String,
    val protocol: String,
    val auth: Boolean,
    val user: String? = null,
    val password: String? = null,
    val parser: String,
    val lastUsed: String,
    val favorite: Boolean,
)

/** Continue-watching shelf item (cross-folder, keyed by server|path/file). */
data class ContinueItem(
    val server: String,
    val path: List<String>,
    val file: String,
    val posSec: Int,
    val durSec: Int,
    val title: String,
    val sub: String,
    val kind: String,
    val coverUrl: String? = null,
) {
    val key: String get() = "$server|${(path + file).joinToString("/")}"
}

data class RecentItem(
    val title: String,
    val sub: String,
    val path: List<String>,
)

enum class DownloadState { DONE, DOWNLOADING, QUEUED, PAUSED, FAILED }

data class Download(
    val id: String,
    val title: String,
    val sub: String,
    val file: String,
    val size: Long,
    val state: DownloadState,
    val progress: Int? = null,
    /** Epoch millis when download finished; 0 = not done. Render computes relative label. */
    val completedAt: Long = 0,
    val server: String,
    val path: List<String>,
    val durSec: Int,
    val url: String = "",
    val downloadedBytes: Long = 0,
    val localUri: String? = null,
    /** SAF tree URI to move the finished file into. Null = keep in app storage. */
    val destDir: String? = null,
)

data class Bookmark(val server: String, val path: List<String>)

/** A user-pinned folder shown as a card on Home. */
data class Shortcut(val server: String, val path: List<String>, val name: String) {
    val key: String get() = "$server|${path.joinToString("/")}"
}
