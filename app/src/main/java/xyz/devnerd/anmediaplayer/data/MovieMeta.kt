package xyz.devnerd.anmediaplayer.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Movie/series metadata (IMDb-sourced via Cinemeta — keyless, public). */
data class MovieInfo(
    val title: String,
    val year: String?,
    val rating: String?,   // imdbRating, e.g. "8.4"
    val poster: String?,   // poster URL
    val genre: String?,
    val plot: String?,
    val runtime: String?,
)

/**
 * Metadata lookup via Stremio's public Cinemeta API (no key, IMDb data):
 *   search:  https://v3-cinemeta.strem.io/catalog/{movie|series}/top/search=<q>.json
 *   detail:  https://v3-cinemeta.strem.io/meta/{movie|series}/<imdbId>.json
 * Cached in-memory by the raw folder/file name; a cached null = looked up, no match.
 */
object OmdbRepo {
    private const val BASE = "https://v3-cinemeta.strem.io"

    private val cache = mutableStateMapOf<String, MovieInfo?>()
    private val inFlight = mutableSetOf<String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val YEAR = Regex("(19|20)\\d{2}")
    private val SE = Regex("(?i)(s\\d{1,2}e\\d{1,2}|season\\s*\\d+|\\bseries\\b)")
    private val JUNK = Regex(
        "(?i)\\b(1080p|2160p|720p|480p|4k|x264|x265|h\\.?264|h\\.?265|hevc|av1|web-?dl|web-?rip|bluray|blu-ray|brrip|bdrip|hdrip|dvdrip|hdtv|aac|ac3|eac3|dts|ddp?5\\.?1|10bit|8bit|dual|multi|audio|esub|msub|hindi|english|bengali|tamil|telugu|korean|dubbed|dub|remastered|extended|uncut|proper|repack|imax|hdr|sdr)\\b",
    )

    /** Strip release junk → (cleanTitle, year?). */
    fun query(name: String): Pair<String, String?> {
        var s = name.replace(Regex("[._]+"), " ")
        val year = YEAR.find(s)?.value
        if (year != null) s = s.substringBefore(year)
        s = JUNK.replace(s, " ")
            .replace(Regex("[\\[\\](){}]"), " ")
            .replace(Regex("[-–]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return s to year
    }

    fun cached(name: String): MovieInfo? = cache[name]
    fun isLookedUp(name: String): Boolean = cache.containsKey(name)

    private val EP = Regex("(?i)s\\d{1,2}e\\d{1,2}")
    private val SEASON = Regex("(?i)\\bseason\\b|^s\\d{1,2}(\\b|$)|^part\\s*\\d+$")

    /**
     * Confident "this name is a movie/TV-series TITLE" — requires a release year and
     * excludes episode/season/part folders. When unsure (no year), return false so we
     * never fetch metadata for category/listing/season folders.
     */
    fun isLikelyTitle(name: String): Boolean {
        val n = name.trim()
        if (EP.containsMatchIn(n)) return false
        if (SEASON.containsMatchIn(n)) return false
        if (YEAR.find(n) == null) return false
        val t = query(n).first
        return t.length >= 2 && t.any { it.isLetter() }
    }

    suspend fun fetch(name: String): MovieInfo? {
        if (cache.containsKey(name)) return cache[name]
        synchronized(inFlight) { if (!inFlight.add(name)) return cache[name] }
        return try {
            val (title, year) = query(name)
            if (title.length < 2) { cache[name] = null; return null }
            // Guess type from the name; try the likely one first, then the other.
            val seriesFirst = SE.containsMatchIn(name)
            val types = if (seriesFirst) listOf("series", "movie") else listOf("movie", "series")
            val info = withContext(Dispatchers.IO) {
                for (type in types) {
                    val id = searchId(type, title, year) ?: continue
                    detail(type, id)?.let { return@withContext it }
                }
                null
            }
            cache[name] = info
            info
        } finally {
            synchronized(inFlight) { inFlight.remove(name) }
        }
    }

    private fun get(url: String): JSONObject? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            resp.body?.string()?.let { JSONObject(it) }
        }
    }.getOrNull()

    /** Search a catalog; pick the best meta id (prefer one matching the year). */
    private fun searchId(type: String, title: String, year: String?): String? {
        val q = java.net.URLEncoder.encode(title, "UTF-8")
        val j = get("$BASE/catalog/$type/top/search=$q.json") ?: return null
        val arr = j.optJSONArray("metas") ?: return null
        if (arr.length() == 0) return null
        var firstId: String? = null
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id").takeIf { it.startsWith("tt") } ?: continue
            if (firstId == null) firstId = id
            if (year != null && m.optString("releaseInfo").contains(year)) return id
        }
        return firstId
    }

    private fun detail(type: String, id: String): MovieInfo? {
        val j = get("$BASE/meta/$type/$id.json")?.optJSONObject("meta") ?: return null
        fun s(k: String) = j.optString(k).takeIf { it.isNotBlank() && it != "N/A" }
        val genres = j.optJSONArray("genres")?.let { g ->
            (0 until g.length()).mapNotNull { g.optString(it).takeIf(String::isNotBlank) }.joinToString(", ").ifBlank { null }
        }
        val title = s("name") ?: return null
        return MovieInfo(
            title = title,
            year = s("year") ?: s("releaseInfo"),
            rating = s("imdbRating"),
            poster = s("poster"),
            genre = s("genre") ?: genres,
            plot = s("description"),
            runtime = s("runtime"),
        )
    }
}
