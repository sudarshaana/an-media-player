package xyz.devnerd.anmediaplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import xyz.devnerd.anmediaplayer.R
import java.util.concurrent.TimeUnit

/** One BDIX provider from the bundled catalog: a brand name + its candidate URLs. */
data class IspServer(val name: String, val urls: List<String>)

/** A catalog entry scored against the detected ISP. */
data class IspMatch(val server: IspServer, val score: Double)

/** Reachability outcome for a single candidate URL. */
data class UrlReach(val url: String, val online: Boolean)

/**
 * Bundled BDIX provider catalog (res/raw/bdix_servers.json, ~394 providers).
 * Loaded once, then fuzzy-matched against the detected ISP org name and probed
 * for reachability on the user's actual network (servers are on-net only).
 */
object BdixCatalog {
    @Volatile private var cache: List<IspServer>? = null

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Tokens that carry no matching signal — every provider name has them. */
    private val STOP = setOf(
        "ftp", "server", "network", "networks", "online", "internet", "communication",
        "communications", "ltd", "limited", "net", "bd", "broadband", "system", "systems",
        "technology", "technologies", "company", "and", "the", "media", "isp", "services",
        "service", "solution", "solutions", "ict",
    )

    fun load(context: Context): List<IspServer> {
        cache?.let { return it }
        val parsed = runCatching {
            val raw = context.resources.openRawResource(R.raw.bdix_servers)
                .bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ua = o.getJSONArray("urls")
                IspServer(o.getString("name"), (0 until ua.length()).map { ua.getString(it) })
            }
        }.getOrDefault(emptyList())
        cache = parsed
        return parsed
    }

    private fun tokenize(s: String): Set<String> = s.lowercase()
        .replace(Regex("\\(.*?\\)"), " ")        // drop parentheticals (brand aliases)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in STOP && it.length > 1 }
        .toSet()

    /**
     * Rank catalog against the detected ISP. Returns matches above a weak threshold,
     * best first. Empty result → caller shows the full catalog instead.
     */
    fun match(catalog: List<IspServer>, isp: IspInfo): List<IspMatch> {
        val ispTokens = tokenize(isp.org).ifEmpty { tokenize(isp.raw) }
        if (ispTokens.isEmpty()) return emptyList()
        return catalog.mapNotNull { s ->
            val nameTokens = tokenize(s.name)
            if (nameTokens.isEmpty()) return@mapNotNull null
            val shared = ispTokens.intersect(nameTokens)
            if (shared.isEmpty()) return@mapNotNull null
            // overlap relative to the catalog name (so short brand names match cleanly)
            val score = shared.size.toDouble() / nameTokens.size
            IspMatch(s, score)
        }.filter { it.score >= 0.5 }
            .sortedByDescending { it.score }
    }

    /** Probe every URL of [server] concurrently; any HTTP response = reachable. */
    suspend fun probe(server: IspServer): List<UrlReach> = coroutineScope {
        server.urls.map { url ->
            async(Dispatchers.IO) { UrlReach(url, reachable(url)) }
        }.map { it.await() }
    }

    private fun reachable(url: String): Boolean = runCatching {
        val req = Request.Builder().url(url).head().build()
        probeClient.newCall(req).execute().use { true }
    }.getOrElse {
        // some servers reject HEAD — retry with GET before giving up
        runCatching {
            probeClient.newCall(Request.Builder().url(url).build()).execute().use { true }
        }.getOrDefault(false)
    }
}
