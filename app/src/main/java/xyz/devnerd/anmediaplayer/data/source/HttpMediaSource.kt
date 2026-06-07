package xyz.devnerd.anmediaplayer.data.source

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import xyz.devnerd.anmediaplayer.data.Entry
import xyz.devnerd.anmediaplayer.data.Server
import java.util.concurrent.TimeUnit

/**
 * Browses HTTP directory listings: nginx/Apache autoindex and h5ai.
 * Strategy: fetch the folder URL, scrape `<a href>` child links (handles
 * autoindex); if that yields nothing and the page is h5ai, fall back to the
 * h5ai JSON API.
 */
class HttpMediaSource(
    private val client: OkHttpClient = defaultClient(),
) : MediaSource {

    override suspend fun list(server: Server, path: List<String>): List<Entry> = withContext(Dispatchers.IO) {
        val url = dirUrl(server, path)
        val req = Request.Builder().url(url).apply {
            if (server.auth && !server.user.isNullOrBlank()) {
                header("Authorization", okhttp3.Credentials.basic(server.user, server.password ?: ""))
            }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            val body = resp.body?.string().orEmpty()
            val scraped = parseAnchors(url, body)
            if (scraped.isNotEmpty()) sortEntries(scraped)
            else sortEntries(tryH5ai(server, path, url))
        }
    }

    override fun fileUrl(server: Server, path: List<String>, file: String): String {
        val base = server.url.trimEnd('/')
        val segs = (path + file).joinToString("/") { encodeSegment(it) }
        return "$base/$segs"
    }

    // ── parsing ──────────────────────────────────────────────

    private val META = Regex("""(\d{1,2}-\w{3}-\d{4}\s+\d{2}:\d{2})\s+([\d.]+[KMGT]?|-)?""")
    private val DATE_OUT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    private fun msToDate(ms: Long?): String? = ms?.takeIf { it > 0 }?.let { DATE_OUT.format(java.util.Date(it)) }

    private fun parseAnchors(baseUrl: String, html: String): List<Entry> {
        val doc = Jsoup.parse(html, baseUrl)

        // h5ai: server-rendered <li class="item"> rows with real metadata.
        val items = doc.select("li.item")
        if (items.isNotEmpty()) {
            val out = LinkedHashMap<String, Entry>()
            for (li in items) {
                if (li.hasClass("folder-parent")) continue
                val a = li.selectFirst("a[href]") ?: continue
                val abs = a.absUrl("href").ifBlank { continue }
                if (!isDescendant(baseUrl, abs)) continue
                val isDir = li.hasClass("folder") || abs.endsWith("/")
                val name = li.selectFirst(".label")?.text()?.takeIf { it.isNotBlank() }
                    ?: Uri.decode(abs.trimEnd('/').substringAfterLast('/'))
                if (name.isBlank()) continue
                val bytes = li.selectFirst(".size")?.attr("data-bytes")?.toLongOrNull()
                val mtime = msToDate(li.selectFirst(".date")?.attr("data-time")?.toLongOrNull())
                out[name] = Entry(name, classify(name, isDir), isDir, size = bytes, mtime = mtime)
            }
            if (out.isNotEmpty()) return out.values.toList()
        }

        // h5ai fallback table (raw HTTP response): <td class="fb-n"> name, fb-d date, fb-s size.
        val fbRows = doc.select("table tr")
        if (fbRows.any { it.selectFirst("td.fb-n") != null }) {
            val out = LinkedHashMap<String, Entry>()
            for (tr in fbRows) {
                val a = tr.selectFirst("td.fb-n a[href]") ?: continue
                val href = a.attr("href")
                if (href.isBlank() || href == ".." || href.startsWith("?")) continue
                val name = a.text().trim().ifBlank { Uri.decode(href.trimEnd('/').substringAfterLast('/')) }
                if (name.isBlank() || name.equals("Parent Directory", true)) continue
                val isDir = href.endsWith("/") || (tr.selectFirst("td.fb-i img")?.attr("alt")?.contains("folder", true) == true)
                val mtime = tr.selectFirst("td.fb-d")?.text()?.trim()?.takeIf { it.isNotBlank() }?.substringBefore(' ')
                val size = sizeFromText(tr.selectFirst("td.fb-s")?.text()?.trim())
                out[name] = Entry(name, classify(name, isDir), isDir, size = size, mtime = mtime)
            }
            if (out.isNotEmpty()) return out.values.toList()
        }

        // generic autoindex (nginx/Apache): resolve every <a href> and keep direct children.
        val out = LinkedHashMap<String, Entry>()
        for (a in doc.select("a[href]")) {
            val abs = a.absUrl("href").ifBlank { continue }
            if (!isDescendant(baseUrl, abs)) continue
            val rel = abs.removePrefix(baseUrl).trimEnd('/')
            if (rel.isBlank() || rel.contains("/")) continue // skip parent + non-direct children
            val isDir = abs.endsWith("/")
            val name = Uri.decode(rel)
            if (name.equals("Parent Directory", true)) continue
            val tail = a.nextSibling()?.toString() ?: ""
            val m = META.find(tail)
            out[name] = Entry(name, classify(name, isDir), isDir, size = m?.groupValues?.getOrNull(2)?.let { parseSize(it) }, mtime = m?.groupValues?.getOrNull(1))
        }
        return out.values.toList()
    }

    /** True if [abs] is the current dir's descendant (not the dir itself or an ancestor). */
    private fun isDescendant(baseUrl: String, abs: String): Boolean {
        val b = baseUrl.substringBefore('?').substringBefore('#')
        return abs.startsWith(b) && abs.trimEnd('/') != b.trimEnd('/')
    }

    /** Parse "1550541 KB" / "441 KB" / "1.5 GB" → bytes. */
    private fun sizeFromText(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val parts = s.trim().split(Regex("\\s+"))
        val num = parts.getOrNull(0)?.replace(",", "")?.toDoubleOrNull() ?: return null
        val mult = when (parts.getOrNull(1)?.uppercase()) {
            "KB" -> 1024.0; "MB" -> 1024.0 * 1024; "GB" -> 1024.0 * 1024 * 1024; "TB" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (num * mult).toLong()
    }

    private fun parseSize(s: String): Long? {
        if (s == "-" || s.isBlank()) return null
        val unit = s.last()
        val num = s.dropLast(if (unit.isLetter()) 1 else 0).toDoubleOrNull() ?: return s.toLongOrNull()
        val mult = when (unit.uppercaseChar()) {
            'K' -> 1024.0; 'M' -> 1024.0 * 1024; 'G' -> 1024.0 * 1024 * 1024; 'T' -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (num * mult).toLong()
    }

    /** h5ai JSON API fallback: POST action=get for the folder. */
    private fun tryH5ai(server: Server, path: List<String>, dirUrl: String): List<Entry> {
        val href = "/" + path.joinToString("/") { Uri.encode(it) }.let { if (it.isEmpty()) "" else "$it/" }
        val endpoint = normalizedBase(server.url) + "_h5ai/public/index.php"
        val form = "action=get&items=" + Uri.encode("""{"href":"$href","what":1}""")
        val body = form.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = Request.Builder().url(endpoint).post(body).build()
        return runCatching {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val json = org.json.JSONObject(r.body?.string().orEmpty())
                val items = json.optJSONObject("items")?.optJSONArray("items") ?: return emptyList()
                buildList {
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        val name = it.optString("href").trimEnd('/').substringAfterLast('/').let { Uri.decode(it) }
                        if (name.isBlank()) continue
                        val isDir = it.optInt("is_managed", 0) == 1 || it.optString("href").endsWith("/")
                        add(Entry(name, classify(name, isDir), isDir, size = it.optLong("size").takeIf { s -> s > 0 }))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun sortEntries(entries: List<Entry>): List<Entry> =
        entries.sortedWith(compareBy<Entry> { !it.isDir }.thenBy { naturalSortKey(it.name) })

    private val DIGITS = Regex("\\d+")
    private fun naturalSortKey(s: String) = DIGITS.replace(s) { it.value.padStart(8, '0') }.lowercase()

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
