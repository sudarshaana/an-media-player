package xyz.devnerd.anmediaplayer.ui.screens.servers

import android.app.Application
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.devnerd.anmediaplayer.data.ApiMirror
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.IspDetector
import xyz.devnerd.anmediaplayer.data.IspInfo
import xyz.devnerd.anmediaplayer.data.Server
import xyz.devnerd.anmediaplayer.data.ServerApi
import xyz.devnerd.anmediaplayer.ui.components.LocalIsTv
import xyz.devnerd.anmediaplayer.ui.components.focusHighlight

enum class Phase { DETECTING, READY }

private val ONLINE = Color(0xFF2E7D32)

/**
 * Holds Suggested-screen state across navigation. Scoped to the nav back-stack
 * entry, so Browse → back preserves detected ISP and scan results; leaving the
 * screen entirely clears it — re-entering refetches the live catalog.
 */
class SuggestedViewModel(app: Application) : AndroidViewModel(app) {
    var phase by mutableStateOf(Phase.DETECTING)
    var isp by mutableStateOf<IspInfo?>(null)
    val matched = mutableStateListOf<ApiMirror>()
    var noMatch by mutableStateOf(false)
    var showAll by mutableStateOf(false)
    var query by mutableStateOf("")
    var catalog by mutableStateOf<List<ApiMirror>>(emptyList())

    // Reachability + in-flight probe state, keyed by mirror url (unique).
    val reachMap: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
    val probingUrls = mutableStateListOf<String>()
    var expandedIsp by mutableStateOf<String?>(null)

    var scanning by mutableStateOf(false)
    var scanIdx by mutableIntStateOf(0)
    var scanTotal by mutableIntStateOf(0)
    private var scanJob: Job? = null

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val info = IspDetector.detect()
            isp = info
            catalog = ServerApi.fetch()
            val matches = if (info?.asn != null) ServerApi.fetch(info.asn) else emptyList()
            matched.clear()
            matched.addAll(matches)
            noMatch = matched.isEmpty()
            showAll = matched.isEmpty()
            // Matched tab is normally one ISP (the detected one) — expand it by default.
            expandedIsp = matched.firstOrNull()?.ispName
            phase = Phase.READY
            if (expandedIsp != null) startScan(matched.map { it.url })
        }
    }

    /** Expanding an ISP card auto-checks its servers; collapsing leaves results cached. */
    fun toggleIsp(ispName: String, urls: List<String>) {
        if (expandedIsp == ispName) {
            expandedIsp = null
        } else {
            expandedIsp = ispName
            startScan(urls)
        }
    }

    fun probe(url: String) {
        if (reachMap.containsKey(url) || url in probingUrls) return
        viewModelScope.launch {
            probingUrls.add(url)
            reachMap[url] = ServerApi.probe(listOf(url)).firstOrNull()?.online ?: false
            probingUrls.remove(url)
        }
    }

    fun startScan(urls: List<String>) {
        val todo = urls.filter { !reachMap.containsKey(it) }
        scanTotal = todo.size
        scanIdx = 0
        scanning = true
        scanJob = viewModelScope.launch {
            for (url in todo) {
                if (!isActive) break
                if (!reachMap.containsKey(url)) {
                    probingUrls.add(url)
                    reachMap[url] = ServerApi.probe(listOf(url)).firstOrNull()?.online ?: false
                    probingUrls.remove(url)
                }
                scanIdx++
            }
            scanning = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanning = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedServersScreen(
    onClose: () -> Unit,
    onBrowse: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val isTv = LocalIsTv.current
    val vm: SuggestedViewModel = viewModel()
    LaunchedEffect(Unit) { vm.start() }

    val shown = if (vm.showAll) {
        if (vm.query.isBlank()) vm.catalog
        else vm.catalog.filter {
            it.serverName.contains(vm.query, true) || it.ispName.contains(vm.query, true) || it.url.contains(vm.query, true)
        }
    } else vm.matched
    val grouped = shown.groupBy { it.ispName }.toList().sortedBy { it.first.lowercase() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Suggested servers") },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.focusHighlight(CircleShape)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { inner ->
        if (vm.phase == Phase.DETECTING) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator()
                    Text("Detecting your ISP…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(inner)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { IspHeader(vm.isp) }

            if (vm.noMatch && !vm.showAll) {
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.matched.isNotEmpty()) {
                        FilterChip(
                            selected = !vm.showAll,
                            onClick = {
                                vm.showAll = false
                                vm.expandedIsp = vm.matched.firstOrNull()?.ispName
                                if (vm.expandedIsp != null) vm.startScan(vm.matched.map { it.url })
                            },
                            label = { Text("Matched (${vm.matched.size})") },
                            modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                        )
                    }
                    FilterChip(
                        selected = vm.showAll,
                        onClick = { vm.showAll = true; vm.expandedIsp = null },
                        label = { Text("All providers") },
                        modifier = Modifier.focusHighlight(RoundedCornerShape(8.dp)),
                    )
                }
            }

            if (vm.showAll && !isTv) {
                item {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Search providers") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                if (vm.scanning) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Testing ${vm.scanIdx} of ${vm.scanTotal}…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { vm.stopScan() }) {
                                Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(18.dp))
                                Text("  Stop")
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (vm.scanTotal == 0) 0f else vm.scanIdx.toFloat() / vm.scanTotal },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    FilledTonalButton(onClick = { vm.startScan(shown.map { it.url }) }, modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(20.dp)), enabled = shown.isNotEmpty()) {
                        Icon(Icons.Outlined.TravelExplore, null, modifier = Modifier.size(18.dp))
                        Text("  Test all (${shown.size})")
                    }
                }
            }

            grouped.forEach { (ispName, mirrors) ->
                val expanded = vm.expandedIsp == ispName
                item(key = "isp_$ispName") {
                    IspGroupHeader(ispName, mirrors.size, expanded, onToggle = { vm.toggleIsp(ispName, mirrors.map { it.url }) })
                }
                if (expanded) {
                    items(mirrors, key = { "${it.ispName}|${it.serverName}|${it.url}" }) { m ->
                        MirrorRow(
                            mirror = m,
                            online = vm.reachMap[m.url],
                            checking = m.url in vm.probingUrls,
                            onBrowse = {
                                val srv = buildSuggestedServer(m.serverName, m.url, m.requiresAuth)
                                AppRepo.addTransient(srv)
                                onBrowse(srv.id)
                            },
                            onAdd = {
                                AppRepo.addServer(buildSuggestedServer(m.serverName, m.url, m.requiresAuth))
                                Toast.makeText(ctx, "Added ${m.serverName}", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    item(key = "test_$ispName") {
                        OutlinedButton(
                            onClick = { vm.startScan(mirrors.map { it.url }) },
                            modifier = Modifier.fillMaxWidth().focusHighlight(RoundedCornerShape(20.dp)),
                            enabled = !vm.scanning,
                        ) {
                            Icon(Icons.Outlined.TravelExplore, null, modifier = Modifier.size(16.dp))
                            Text("  Test all servers (${mirrors.size})")
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun IspHeader(isp: IspInfo?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
private fun IspGroupHeader(ispName: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .focusHighlight(RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    prettyIspName(ispName).ifBlank { "Unknown ISP" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("$count server${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MirrorRow(mirror: ApiMirror, online: Boolean?, checking: Boolean, onBrowse: () -> Unit, onAdd: () -> Unit) {
    val enabled = online == true
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp)) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(mirror.serverName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (mirror.requiresAuth) Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReachDot(online = online, checking = checking)
                        Text(mirror.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBrowse, enabled = enabled, modifier = Modifier.weight(1f).focusHighlight(RoundedCornerShape(20.dp))) {
                    Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Text("  Browse")
                }
                FilledTonalButton(onClick = onAdd, enabled = enabled, modifier = Modifier.weight(1f).focusHighlight(RoundedCornerShape(20.dp))) {
                    Text("Add to server")
                }
            }
        }
    }
}

@Composable
private fun ReachDot(online: Boolean?, checking: Boolean) {
    when {
        checking -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        online == true -> Box(Modifier.size(9.dp).clip(CircleShape).background(ONLINE))
        online == false -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(12.dp))
        else -> Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
    }
}

/** "BUSINESS NETWORK (FTPBD) FTP SERVER" → "Business Network (FTPBD)". */
private fun prettyIspName(raw: String): String =
    raw.replace(Regex("\\s*FTP SERVER\\s*$", RegexOption.IGNORE_CASE), "")
        .split(" ")
        .joinToString(" ") { w -> if (w.length > 3 && w == w.uppercase() && !w.startsWith("(")) w.lowercase().replaceFirstChar { it.uppercase() } else w }
        .trim()

private fun buildSuggestedServer(name: String, url: String, requiresAuth: Boolean): Server {
    val clean = url.trim()
    val parser = if (Regex("h5ai|\\?action=").containsMatchIn(clean)) "h5ai JSON API" else "auto"
    return Server(
        id = "srv_" + Integer.toHexString(clean.hashCode()),
        name = prettyIspName(name),
        url = clean,
        protocol = if (clean.startsWith("https", true)) "HTTPS" else "HTTP",
        auth = requiresAuth,
        user = null,
        password = null,
        parser = parser,
        lastUsed = "",
        favorite = false,
    )
}
