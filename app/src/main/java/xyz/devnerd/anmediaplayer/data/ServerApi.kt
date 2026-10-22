package xyz.devnerd.anmediaplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import xyz.devnerd.anmediaplayer.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * One ISP-specific mirror of a published server group, e.g. "DhakaFlix FTP
 * Servers" via "Carnival Internet". [ispName] is the per-mirror ISP display
 * label — grouping mirrors by [ispName] is how the Suggested-servers screen
 * shows "under this ISP, these servers".
 */
data class ApiMirror(val serverName: String, val url: String, val ispName: String, val requiresAuth: Boolean)

/** Reachability outcome for a single candidate URL. */
data class UrlReach(val url: String, val online: Boolean)

/**
 * Backend-published server catalog (Django: GET /api/servers/, see
 * docs/server-api-design.md). Replaces the old bundled res/raw/bdix_servers.json
 * — fetched fresh on every Suggested-servers screen open so admin-approved
 * mirrors show up without an app update.
 */
object ServerApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    /** GET /api/servers/[?isp_asn=AS...] — published servers' approved mirrors, flattened. */
    suspend fun fetch(ispAsn: String? = null): List<ApiMirror> = withContext(Dispatchers.IO) {
        val url = (BuildConfig.API_BASE_URL + "api/servers/").toHttpUrl().newBuilder()
            .apply { if (!ispAsn.isNullOrBlank()) addQueryParameter("isp_asn", ispAsn) }
            .build()
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                (0 until arr.length()).flatMap { i ->
                    val o = arr.getJSONObject(i)
                    val serverName = o.getString("name")
                    val mirrors = o.getJSONArray("mirrors")
                    (0 until mirrors.length()).map { j ->
                        val m = mirrors.getJSONObject(j)
                        ApiMirror(serverName, m.getString("url"), m.optString("name"), m.optBoolean("requires_auth"))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * POST /api/servers/submit/ — best-effort, fire-and-forget. No-op without an
     * ASN (server requires it); errors and duplicate/pending/rejected outcomes
     * are swallowed by design (see docs/server-api-design.md) — nothing is
     * surfaced to the UI either way.
     */
    suspend fun submit(name: String, url: String, ispAsn: String?, ispName: String?, requiresAuth: Boolean, parser: String?) {
        if (ispAsn.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("name", name)
                    put("url", url)
                    put("isp_asn", ispAsn)
                    put("isp_name", ispName.orEmpty())
                    put("requires_auth", requiresAuth)
                    if (!parser.isNullOrBlank()) put("parser", parser)
                }.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url(BuildConfig.API_BASE_URL + "api/servers/submit/")
                    .post(body)
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    /** Probe [urls] concurrently; any HTTP response = reachable. */
    suspend fun probe(urls: List<String>): List<UrlReach> = coroutineScope {
        urls.map { url ->
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
