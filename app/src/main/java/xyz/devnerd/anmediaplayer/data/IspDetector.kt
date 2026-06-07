package xyz.devnerd.anmediaplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Resolved network identity used to match against the bundled BDIX catalog. */
data class IspInfo(
    val org: String,      // human ISP/ASN org name, e.g. "Carnival Internet"
    val asn: String?,     // "AS138296" if known
    val ip: String?,      // public IP
    val raw: String,      // full org/isp string as returned
)

/**
 * Resolves the user's public-IP → ISP org name. BDIX servers are on-net only, so
 * the org name is what we fuzzy-match against [BdixCatalog] entry names.
 * Tries ipinfo.io (HTTPS) first, falls back to ip-api.com.
 */
object IspDetector {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun detect(): IspInfo? = withContext(Dispatchers.IO) {
        ipinfo() ?: ipApi()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", "AnMediaPlayer").build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    /** ipinfo.io/json → { "ip": "...", "org": "AS138296 Carnival Internet Ltd" } */
    private fun ipinfo(): IspInfo? {
        val body = get("https://ipinfo.io/json") ?: return null
        return runCatching {
            val o = JSONObject(body)
            val raw = o.optString("org")
            if (raw.isBlank()) return null
            val asn = Regex("^AS\\d+", RegexOption.IGNORE_CASE).find(raw)?.value
            val org = raw.removePrefix(asn ?: "").trim()
            IspInfo(org = org.ifBlank { raw }, asn = asn, ip = o.optString("ip").ifBlank { null }, raw = raw)
        }.getOrNull()
    }

    /** ip-api.com/json → { "isp": "...", "org": "...", "as": "AS138296 ...", "query": "1.2.3.4" } */
    private fun ipApi(): IspInfo? {
        val body = get("http://ip-api.com/json") ?: return null
        return runCatching {
            val o = JSONObject(body)
            if (o.optString("status") != "success") return null
            val isp = o.optString("isp").ifBlank { o.optString("org") }
            if (isp.isBlank()) return null
            val asField = o.optString("as")
            val asn = Regex("^AS\\d+", RegexOption.IGNORE_CASE).find(asField)?.value
            IspInfo(org = isp, asn = asn, ip = o.optString("query").ifBlank { null }, raw = listOf(isp, asField).filter { it.isNotBlank() }.joinToString(" · "))
        }.getOrNull()
    }
}
