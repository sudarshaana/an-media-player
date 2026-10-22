package xyz.devnerd.anmediaplayer.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// Formatting + sorting helpers, mirroring data.jsx.

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

fun fmtSize(bytes: Long?): String {
    if (bytes == null) return ""
    var n = bytes.toDouble()
    var i = 0
    while (n >= 1024 && i < UNITS.size - 1) { n /= 1024; i++ }
    val v = if (n >= 100 || i == 0) n.roundToInt().toString() else String.format(Locale.US, "%.1f", n)
    return "$v ${UNITS[i]}"
}

/** Relative "x ago" label from an epoch-millis timestamp; falls back to "Saved" if unset. */
fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0) return "Saved"
    val s = ((now - epochMillis) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "Just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        s < 604800 -> "${s / 86400}d ago"
        else -> PRETTY.format(java.util.Date(epochMillis))
    }
}

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val PRETTY = SimpleDateFormat("MMM d, yyyy", Locale.US)

fun fmtDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching { PRETTY.format(ISO.parse(iso)!!) }.getOrDefault(iso)
}

fun fmtDur(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    fun pad(n: Int) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${pad(m)}:${pad(ss)}" else "$m:${pad(ss)}"
}

/** Natural/numeric compare so Ep2 sorts before Ep10. */
private val DIGITS = Regex("\\d+")
private fun padNumbers(s: String) = DIGITS.replace(s) { it.value.padStart(8, '0') }
fun naturalCompare(a: String, b: String): Int =
    padNumbers(a).compareTo(padNumbers(b), ignoreCase = true)

private val YEAR = Regex("^\\(?\\d{4}\\)?$")

/** Title-case a messy folder name; keep a (year) token intact. */
fun cleanTitle(name: String): String =
    name.replace(Regex("[._-]+"), " ").replace(Regex("\\s+"), " ").trim()
        .split(" ")
        .joinToString(" ") { w ->
            if (YEAR.matches(w)) w else w.replaceFirstChar { it.uppercaseChar() }
        }

/** Display name for a media file: episode vs movie vs raw. */
data class PrettyName(val primary: String, val secondary: String, val ep: Int? = null, val season: Int? = null) {
    /** Compact row label for the in-player playlist (season-qualified so cross-season lists stay readable). */
    val playlistLabel: String get() = if (ep != null && season != null) "S$season · $primary" else primary
}

private val IC = RegexOption.IGNORE_CASE
// Episode markers, most specific first. Groups: 1 = season (may be empty), 2 = episode, 3 = range end (optional).
private val EP_PATTERNS = listOf(
    // S01E05 · S1E5 · S01 E05 · S01.E05 · S01E01–E02
    Regex("(?<![a-z0-9])s(\\d{1,2})[ ._-]?e(\\d{1,4})(?:\\s*[-–]\\s*e?(\\d{1,4}))?(?![0-9])", IC),
    // 1x05
    Regex("(?<![a-z0-9])(\\d{1,2})x(\\d{2,3})()(?![a-z0-9])", IC),
    // E01 · EP01 · Ep.01 · Episode 1 (no season)
    Regex("(?<![a-z0-9])()(?:episode|ep|e)[ ._-]?(\\d{1,4})(?:\\s*[-–]\\s*e?(\\d{1,4}))?(?![0-9])", IC),
)
// Bare trailing number: "Arang and Magistrate 01". 2–3 digits so "Avenue 5"-style titles stay titles.
private val EP_TRAILING = Regex("[ ._-]+(\\d{2,3})$")
private val MOVIE_YEAR = Regex("^(.*?)\\s*\\((\\d{4})\\)")
// First release-tag token ends an episode title ("…E02.Red.Dress.720p.NF…" → "Red Dress").
private val RELEASE_TAG = Regex(
    "^(?:\\d{3,4}p|4k|uhd|hdr|web|webrip|web-?dl|web-?hd|hdtv|hdrip|bluray|brrip|bdrip|dvdrip|nf|amzn|dsnp|hulu|hmax|atvp|mx|jio|" +
        "x26[45]|h26[45]|hevc|avc|aac\\S*|ddp?\\S*|10bit|6ch|\\d+mb|esub|msubs|kor|hin-eng|dual|multi|dubbed|hindi|korean|english|" +
        "thai|tamil|telugu|bengali|bangla|chinese|japanese|french|spanish)$",
    IC,
)

private fun spaced(s: String) = s.replace(Regex("[._]+"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '–', '·')

/** Episode identity pulled out of a messy release filename. */
private data class EpisodeInfo(val show: String, val season: Int?, val ep: Int, val epEnd: Int?, val title: String?)

private fun episodeTitle(rest: String, ep: Int): String? {
    val cut = rest.split(Regex("\\s[-–]\\s|[\\[(]"))[0]
    val t = spaced(cut).split(' ').takeWhile { it.isNotBlank() && !RELEASE_TAG.matches(it) }.joinToString(" ")
    return t.takeIf { it.isNotBlank() && !it.equals("Episode $ep", ignoreCase = true) }
}

private fun parseEpisode(stem: String): EpisodeInfo? {
    EP_PATTERNS.firstNotNullOfOrNull { it.find(stem) }?.let { m ->
        val ep = m.groupValues[2].toInt()
        return EpisodeInfo(
            show = spaced(stem.substring(0, m.range.first)),
            season = m.groupValues[1].toIntOrNull(),
            ep = ep,
            epEnd = m.groupValues[3].toIntOrNull()?.takeIf { it > ep },
            title = episodeTitle(stem.substring(m.range.last + 1), ep),
        )
    }
    if (MOVIE_YEAR.containsMatchIn(stem)) return null
    return EP_TRAILING.find(stem)?.let { m ->
        EpisodeInfo(spaced(stem.substring(0, m.range.first)), null, m.groupValues[1].toInt(), null, null)
    }
}

private fun stem(name: String) = name.replace(Regex("\\.[a-z0-9]{2,4}$", IC), "")

fun prettyName(name: String): PrettyName {
    val n = stem(name)
    parseEpisode(n)?.let { e ->
        val head = if (e.epEnd != null) "Episodes ${e.ep}–${e.epEnd}" else "Episode ${e.ep}"
        val secondary = listOfNotNull(e.show.ifBlank { null }, e.season?.let { "S" + it.toString().padStart(2, '0') }).joinToString(" · ")
        return PrettyName(e.title?.let { "$head · $it" } ?: head, secondary, e.ep, e.season)
    }
    MOVIE_YEAR.find(n)?.let { m ->
        return PrettyName(m.groupValues[1].trim(), m.groupValues[2])
    }
    return PrettyName(spaced(n), "")
}

/** (season, episode) when the filename parses as an episode — orders a playlist regardless of release-name noise. */
fun episodeSortKey(name: String): Pair<Int, Int>? =
    parseEpisode(stem(name))?.let { (it.season ?: 0) to it.ep }
