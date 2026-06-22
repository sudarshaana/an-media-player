package xyz.devnerd.anmediaplayer.ui.screens.servers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.Server

private val PROTOCOL = Regex("^(https?|ftps?|sftp|webdav|smb):", RegexOption.IGNORE_CASE)

private fun detectProtocol(url: String): String? =
    PROTOCOL.find(url)?.groupValues?.get(1)?.uppercase()

private fun detectParser(url: String, proto: String?): String? = when {
    Regex("h5ai|\\?action=").containsMatchIn(url) -> "h5ai JSON API"
    Regex("^https?:", RegexOption.IGNORE_CASE).containsMatchIn(url) -> "Apache / nginx index"
    proto != null -> "$proto listing"
    else -> null
}

/**
 * Live URL-pattern guess is just a hint; this does the real check on Connect —
 * fetches the root folder and falls back to "WEB" (raw-browser mode) when no
 * listing structure (h5ai/autoindex) is found, instead of showing an empty folder.
 */
private suspend fun probeParser(url: String, proto: String?, auth: Boolean, user: String, pass: String): String {
    if (proto != "HTTP" && proto != "HTTPS") return detectParser(url, proto) ?: (proto ?: "auto")
    val probeServer = xyz.devnerd.anmediaplayer.data.Server(
        id = "", name = "", url = url.trim(), protocol = proto, auth = auth,
        user = user.ifBlank { null }, password = pass.ifBlank { null },
        parser = "auto", lastUsed = "", favorite = false,
    )
    return try {
        val entries = xyz.devnerd.anmediaplayer.data.source.HttpMediaSource().list(probeServer, emptyList())
        if (entries.isNotEmpty()) detectParser(url, proto) ?: "Apache / nginx index" else "WEB"
    } catch (e: Exception) {
        "WEB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    onClose: () -> Unit,
    onConnected: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
    editServer: Server? = null,
) {
    val editing = editServer != null
    var name by remember { mutableStateOf(editServer?.name ?: "") }
    var url by remember { mutableStateOf(editServer?.url ?: "http://") }
    var needAuth by remember { mutableStateOf(editServer?.auth ?: false) }
    var user by remember { mutableStateOf(editServer?.user ?: "") }
    var pass by remember { mutableStateOf(editServer?.password ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val proto = detectProtocol(url)
    val parser = detectParser(url, proto)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (editing) "Edit server" else "Add server") },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("NAME") },
                singleLine = true,
                placeholder = { Text("Auto from address") },
                modifier = Modifier.fillMaxWidth(),
            )

            // address
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("ADDRESS") },
                singleLine = true,
                placeholder = { Text("http://host/path/") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                trailingIcon = proto?.let {
                    {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )

            if (parser != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        buildString { append("Parser: $parser · generic anchor scrape fallback") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // requires sign-in
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Requires sign-in", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Basic auth / credentials", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = needAuth, onCheckedChange = { needAuth = it })
            }

            AnimatedVisibility(needAuth) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("USERNAME") }, singleLine = true,
                        placeholder = { Text("username") }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("PASSWORD") }, singleLine = true,
                        placeholder = { Text("••••••••") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(if (showPass) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "Toggle password", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                        Text("Stored with Keystore-backed encryption (DataStore + Tink).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Button(
                onClick = {
                    if (connecting) return@Button
                    connecting = true
                    scope.launch {
                        val resolvedParser = probeParser(url, proto, needAuth, user, pass)
                        val server = buildServer(name, url, proto, resolvedParser, needAuth, user, pass, editServer)
                        AppRepo.addServer(server)
                        onConnected(server.id)
                    }
                },
                enabled = proto != null && !connecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text(if (editing) "  Saving…" else "  Connecting…")
                } else {
                    Icon(Icons.Outlined.Link, null, modifier = Modifier.size(18.dp))
                    Text(if (editing) "  Save" else "  Connect")
                }
            }

            // saved
            if (!editing) {
            Text("SAVED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppRepo.servers.forEach { s ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { url = s.url },
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            }
        }
    }
}

private fun buildServer(name: String, url: String, proto: String?, parser: String?, auth: Boolean, user: String, pass: String, existing: Server? = null): Server {
    val clean = url.trim()
    val host = runCatching { java.net.URI(clean).host }.getOrNull()
        ?: clean.substringAfter("://").substringBefore('/').ifBlank { "Server" }
    return Server(
        id = existing?.id ?: ("srv_" + Integer.toHexString(clean.hashCode())),
        name = name.trim().ifBlank { host },
        url = clean,
        protocol = proto ?: "HTTP",
        auth = auth,
        user = user.ifBlank { null },
        password = pass.ifBlank { null },
        parser = parser ?: "auto",
        lastUsed = existing?.lastUsed ?: "",
        favorite = existing?.favorite ?: false,
    )
}
