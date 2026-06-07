package xyz.devnerd.anmediaplayer.ui.screens.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.BdixCatalog
import xyz.devnerd.anmediaplayer.data.IspDetector
import xyz.devnerd.anmediaplayer.data.IspInfo
import xyz.devnerd.anmediaplayer.data.IspServer
import xyz.devnerd.anmediaplayer.data.Server
import xyz.devnerd.anmediaplayer.data.UrlReach

private enum class Phase { DETECTING, READY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedServersScreen(
    onClose: () -> Unit,
    onSaved: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf(Phase.DETECTING) }
    var isp by remember { mutableStateOf<IspInfo?>(null) }
    val matched = remember { mutableStateListOf<IspServer>() }
    var noMatch by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<IspServer?>(null) }

    LaunchedEffect(Unit) {
        val catalog = BdixCatalog.load(ctx)
        val info = IspDetector.detect()
        isp = info
        val matches = if (info != null) BdixCatalog.match(catalog, info) else emptyList()
        matched.clear()
        matched.addAll(matches.map { it.server })
        noMatch = matched.isEmpty()
        showAll = matched.isEmpty()
        phase = Phase.READY
    }

    val catalog = remember { BdixCatalog.load(ctx) }
    val shown = if (showAll) {
        if (query.isBlank()) catalog
        else catalog.filter { it.name.contains(query, ignoreCase = true) || it.urls.any { u -> u.contains(query, ignoreCase = true) } }
    } else matched

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Suggested servers") },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { inner ->
        if (phase == Phase.DETECTING) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator()
                    Text("Detecting your ISP…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { IspHeader(isp) }

            if (!showAll && !noMatch) {
                item {
                    Text(
                        "${matched.size} provider${if (matched.size == 1) "" else "s"} matched your network. Tap one to open it, then save the address that responds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
            if (noMatch && !showAll) {
                item {
                    Text(
                        "No provider matched your ISP automatically. Browse the full list below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (matched.isNotEmpty()) {
                        FilterChip(selected = !showAll, onClick = { showAll = false }, label = { Text("Matched (${matched.size})") })
                    }
                    FilterChip(selected = showAll, onClick = { showAll = true }, label = { Text("All providers") })
                }
            }

            if (showAll) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search providers") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            items(shown, key = { it.name }) { s ->
                IspRow(s = s, onClick = { detail = s })
            }
        }
    }

    detail?.let { server ->
        SuggestedDetailSheet(
            server = server,
            onDismiss = { detail = null },
            onSave = { url ->
                val s = buildSuggestedServer(server.name, url)
                AppRepo.addServer(s)
                detail = null
                onSaved(s.id)
            },
        )
    }
}

@Composable
private fun IspHeader(isp: IspInfo?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Detected ISP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    isp?.org?.takeIf { it.isNotBlank() } ?: "Unknown — check your connection",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                isp?.let { i ->
                    val sub = listOfNotNull(i.asn, i.ip).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IspRow(s: IspServer, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(prettyIspName(s.name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${s.urls.size} address${if (s.urls.size == 1) "" else "es"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestedDetailSheet(
    server: IspServer,
    onDismiss: () -> Unit,
    onSave: (url: String) -> Unit,
) {
    val ctx = LocalContext.current
    var probing by remember { mutableStateOf(true) }
    val reach = remember { mutableStateListOf<UrlReach>() }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(server.name) {
        probing = true
        reach.clear()
        val results = BdixCatalog.probe(server)
        reach.addAll(results)
        // auto-select first reachable, else first url
        selected = results.firstOrNull { it.online }?.url ?: server.urls.firstOrNull()
        probing = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prettyIspName(server.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (probing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Testing reachability…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val online = reach.count { it.online }
                    Text("$online of ${reach.size} reachable on this network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            server.urls.forEach { url ->
                val r = reach.firstOrNull { it.url == url }
                UrlRow(
                    url = url,
                    online = r?.online,
                    checking = probing && r == null,
                    selected = selected == url,
                    onSelect = { selected = url },
                )
            }

            Button(
                onClick = { selected?.let(onSave) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Save server") }
        }
    }
}

@Composable
private fun UrlRow(url: String, online: Boolean?, checking: Boolean, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        ReachBadge(online = online, checking = checking)
    }
}

@Composable
private fun ReachBadge(online: Boolean?, checking: Boolean) {
    when {
        checking -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        online == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
            Text("Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        online == false -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
            Text("Offline", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Icon(Icons.Outlined.CheckCircle, null, tint = Color.Transparent, modifier = Modifier.size(14.dp))
    }
}

/** "BUSINESS NETWORK (FTPBD) FTP SERVER" → "Business Network (FTPBD)". */
private fun prettyIspName(raw: String): String =
    raw.replace(Regex("\\s*FTP SERVER\\s*$", RegexOption.IGNORE_CASE), "")
        .split(" ")
        .joinToString(" ") { w -> if (w.length > 3 && w == w.uppercase() && !w.startsWith("(")) w.lowercase().replaceFirstChar { it.uppercase() } else w }
        .trim()

private fun buildSuggestedServer(ispName: String, url: String): Server {
    val clean = url.trim()
    val parser = if (Regex("h5ai|\\?action=").containsMatchIn(clean)) "h5ai JSON API" else "auto"
    return Server(
        id = "srv_" + Integer.toHexString(clean.hashCode()),
        name = prettyIspName(ispName),
        url = clean,
        protocol = if (clean.startsWith("https", true)) "HTTPS" else "HTTP",
        auth = false,
        user = null,
        password = null,
        parser = parser,
        lastUsed = "",
        favorite = false,
    )
}
