package xyz.devnerd.anmediaplayer.data.source

import android.net.Uri
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.EntryType
import xyz.devnerd.anmediaplayer.data.Server

/** A browsable/streamable backend. HTTP today; FTP/SFTP/WebDAV later. */
interface MediaSource {
    /** List a folder. Throws on network/parse failure. */
    suspend fun list(server: Server, path: List<String>): List<Entry>

    /** Direct stream/download URL for a file. */
    fun fileUrl(server: Server, path: List<String>, file: String): String
}

private val VIDEO_EXTS = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "m2ts", "flv", "wmv", "mpg", "mpeg", "3gp")
private val SUB_EXTS = setOf("srt", "vtt", "ass", "ssa", "sub")
private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

fun classify(name: String, isDir: Boolean): EntryType = when {
    isDir -> EntryType.DIR
    else -> when (name.substringAfterLast('.', "").lowercase()) {
        in VIDEO_EXTS -> EntryType.VIDEO
        in SUB_EXTS -> EntryType.SUBTITLE
        in IMAGE_EXTS -> EntryType.IMAGE
        else -> EntryType.OTHER
    }
}

/** Base URL with a guaranteed single trailing slash. */
fun normalizedBase(url: String): String = url.trimEnd('/') + "/"

/**
 * Percent-encode a path segment like PHP rawurlencode (what h5ai/most servers
 * use). Android's Uri.encode leaves `( ) ! ' *` literal, which mismatches h5ai
 * folder names like "Movies (1080p)" → "Movies%20%281080p%29".
 */
fun encodeSegment(s: String): String = Uri.encode(s)
    .replace("(", "%28").replace(")", "%29")
    .replace("!", "%21").replace("'", "%27").replace("*", "%2A")

fun dirUrl(server: Server, path: List<String>): String {
    val base = server.url.trimEnd('/')
    val tail = path.joinToString("/") { encodeSegment(it) }
    return if (tail.isEmpty()) "$base/" else "$base/$tail/"
}

/** The current folder's videos in natural order — the playback playlist. */
fun naturalVideoSort(entries: List<Entry>): List<Entry> =
    entries.filter { it.type == EntryType.VIDEO }
        .sortedWith { a, b -> naturalKey(a.name).compareTo(naturalKey(b.name)) }

private val DIGITS = Regex("\\d+")
private fun naturalKey(s: String) = DIGITS.replace(s) { it.value.padStart(8, '0') }.lowercase()

/** Matching sibling subtitle for a video (same stem), if present in the listing. */
fun matchSubtitle(entries: List<Entry>, videoFile: String): Entry? {
    val stem = videoFile.substringBeforeLast('.')
    return entries.firstOrNull { it.type == EntryType.SUBTITLE && it.name.startsWith(stem) }
}
