package xyz.devnerd.anmediaplayer.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import xyz.devnerd.anmediaplayer.BuildConfig
import java.net.URLEncoder

data class OnlineSubtitle(val fileId: Int, val label: String, val language: String)

data class DownloadedSubtitle(val url: String, val fileName: String)

/** OpenSubtitles REST API v1 (https://api.opensubtitles.com). Requires a free API key. */
object OpenSubtitlesApi {
    private const val BASE = "https://api.opensubtitles.com/api/v1"
    private val client = OkHttpClient()

    private fun req(url: String) = Request.Builder().url(url)
        .header("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
        .header("User-Agent", "AnMediaPlayer v1.0")
        .header("Content-Type", "application/json")

    fun search(query: String): Result<List<OnlineSubtitle>> = runCatching {
        if (BuildConfig.OPENSUBTITLES_API_KEY.isBlank()) error("No OpenSubtitles API key configured.")
        val url = "$BASE/subtitles?query=${URLEncoder.encode(query, "UTF-8")}&languages=en"
        client.newCall(req(url).build()).execute().use { res ->
            if (!res.isSuccessful) error("Search failed (${res.code}).")
            val body = res.body?.string().orEmpty()
            val data = JSONObject(body).optJSONArray("data") ?: return@use emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val attrs = item.getJSONObject("attributes")
                    val files = attrs.optJSONArray("files") ?: continue
                    if (files.length() == 0) continue
                    val fileId = files.getJSONObject(0).optInt("file_id", -1)
                    if (fileId < 0) continue
                    val release = attrs.optString("release").ifBlank { files.getJSONObject(0).optString("file_name", "Subtitle") }
                    add(OnlineSubtitle(fileId, release, attrs.optString("language", "en")))
                }
            }
        }
    }

    fun download(fileId: Int): Result<DownloadedSubtitle> = runCatching {
        val body = JSONObject().put("file_id", fileId).toString().toRequestBody("application/json".toMediaType())
        val request = req("$BASE/download").post(body).build()
        client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) error("Download failed (${res.code}).")
            val json = JSONObject(res.body?.string().orEmpty())
            val link = json.optString("link").ifBlank { null } ?: error("No download link returned.")
            DownloadedSubtitle(link, json.optString("file_name", "subtitle.srt"))
        }
    }
}
