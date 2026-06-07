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
data class PrettyName(val primary: String, val secondary: String, val ep: Int? = null)

private val SE = Regex("S(\\d{2})E(\\d{2})", RegexOption.IGNORE_CASE)
private val MOVIE_YEAR = Regex("^(.*?)\\s*\\((\\d{4})\\)")

fun prettyName(name: String): PrettyName {
    val n = name.replace(Regex("\\.[a-z0-9]+$", RegexOption.IGNORE_CASE), "")
    SE.find(n)?.let { m ->
        val show = n.split(Regex("\\.S\\d{2}E\\d{2}", RegexOption.IGNORE_CASE))[0]
            .replace(Regex("[._]"), " ").trim()
        val epNum = m.groupValues[2].toInt()
        return PrettyName("Episode $epNum", "$show · S${m.groupValues[1]}", epNum)
    }
    MOVIE_YEAR.find(n)?.let { m ->
        return PrettyName(m.groupValues[1].trim(), m.groupValues[2])
    }
    return PrettyName(n.replace(Regex("[._]"), " "), "")
}
