package xyz.devnerd.anmediaplayer.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ServerStatus { CHECKING, ONLINE, OFFLINE }

/** Reachability check for saved servers, run at launch. Online = server responded at all. */
object ServerHealth {
    val status = mutableStateMapOf<String, ServerStatus>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    suspend fun check(server: Server) = withContext(Dispatchers.IO) {
        status[server.id] = ServerStatus.CHECKING
        val online = runCatching {
            val req = Request.Builder().url(server.url).apply {
                if (server.auth && !server.user.isNullOrBlank()) {
                    header("Authorization", Credentials.basic(server.user, server.password ?: ""))
                }
            }.build()
            client.newCall(req).execute().use { true } // any HTTP response = reachable
        }.getOrDefault(false)
        status[server.id] = if (online) ServerStatus.ONLINE else ServerStatus.OFFLINE
    }

    suspend fun checkAll(servers: List<Server>) = coroutineScope {
        servers.map { async { check(it) } }.awaitAll()
    }

    /** Confirmed offline (not merely unchecked / still checking). */
    fun isOffline(serverId: String): Boolean = status[serverId] == ServerStatus.OFFLINE
}
