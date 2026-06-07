package xyz.devnerd.anmediaplayer.ui.screens.servers

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.ErrorOutline
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
import xyz.devnerd.anmediaplayer.data.AppRepo
import xyz.devnerd.anmediaplayer.data.BdixCatalog
import xyz.devnerd.anmediaplayer.data.IspDetector
import xyz.devnerd.anmediaplayer.data.IspInfo
import xyz.devnerd.anmediaplayer.data.IspServer
import xyz.devnerd.anmediaplayer.data.Server
import xyz.devnerd.anmediaplayer.data.UrlReach

enum class Phase { DETECTING, READY }

private val ONLINE = Color(0xFF2E7D32)

/**
 * Holds Suggested-screen state across navigation. Scoped to the nav back-stack
 * entry, so Browse → back preserves detected ISP, scan results and expansion;
 * leaving the screen entirely clears it.
 */
class SuggestedViewModel(app: Application) : AndroidViewModel(app) {
    var phase by mutableStateOf(Phase.DETECTING)
    var isp by mutableStateOf<IspInfo?>(null)
    val matched = mutableStateListOf<IspServer>()
    var noMatch by mutableStateOf(false)
    var showAll by mutableStateOf(false)
    var query by mutableStateOf("")
    var catalog by mutableStateOf<List<IspServer>>(emptyList())

    // Reachability per ISP (by name). Absent = not tested yet.
    val reachMap = mutableStateMapOf<String, List<UrlReach>>()
    var expanded by mutableStateOf<String?>(null)
    var probingIsp by mutableStateOf<String?>(null)

    var scanning by mutableStateOf(false)
    var scanIdx by mutableIntStateOf(0)
    var scanTotal by mutableIntStateOf(0)
    private var scanJob: Job? = null

    private var started = false

    fun start() {
        if (started) return
        started = true
        catalog = BdixCatalog.load(getApplication())
        viewModelScope.launch {
            val info = IspDetector.detect()
            isp = info
            val matches = if (info != null) BdixCatalog.match(catalog, info) else emptyList()
            matched.clear()
            matched.addAll(matches.map { it.server })
            noMatch = matched.isEmpty()
            showAll = matched.isEmpty()
            phase = Phase.READY
        }
    }

    fun probe(s: IspServer) {
        if (reachMap[s.name] != null || probingIsp == s.name) return
        viewModelScope.launch {
            probingIsp = s.name
            reachMap[s.name] = BdixCatalog.probe(s)
            probingIsp = null
        }
    }

    fun toggle(s: IspServer) {
        if (expanded == s.name) expanded = null
        else { expanded = s.name; probe(s) }
    }

    fun startScan(list: List<IspServer>) {
        scanTotal = list.size
        scanIdx = 0
        scanning = true
        scanJob = viewModelScope.launch {
            for (s in list) {
                if (!isActive) break
                if (reachMap[s.name] == null) reachMap[s.name] = BdixCatalog.probe(s)
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
    val vm: SuggestedViewModel = viewModel()
    LaunchedEffect(Unit) { vm.start() }

    val shown = if (vm.showAll) {
        if (vm.query.isBlank()) vm.catalog
        else vm.catalog.filter { it.name.contains(vm.query, true) || it.urls.any { u -> u.contains(vm.query, true) } }
    } else vm.matched

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
        if (vm.phase == Phase.DETECTING) {
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
                        FilterChip(selected = !vm.showAll, onClick = { vm.showAll = false }, label = { Text("Matched (${vm.matched.size})") })
                    }
                    FilterChip(selected = vm.showAll, onClick = { vm.showAll = true }, label = { Text("All providers") })
                }
            }

            if (vm.showAll) {
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
                    FilledTonalButton(onClick = { vm.startScan(shown) }, modifier = Modifier.fillMaxWidth(), enabled = shown.isNotEmpty()) {
                        Icon(Icons.Outlined.TravelExplore, null, modifier = Modifier.size(18.dp))
                        Text("  Test all (${shown.size})")
                    }
                }
            }

            items(shown, key = { it.name }) { s ->
                IspCard(
                    server = s,
                    reach = vm.reachMap[s.name],
                    expanded = vm.expanded == s.name,
                    probing = vm.probingIsp == s.name,
                    onToggle = { vm.toggle(s) },
                    onBrowse = { url ->
                        val srv = buildSuggestedServer(s.name, url)
                        AppRepo.addTransient(srv)
                        onBrowse(srv.id)
                    },
                    onAdd = { url ->
                        AppRepo.addServer(buildSuggestedServer(s.name, url))
                        Toast.makeText(ctx, "Added ${prettyIspName(s.name)}", Toast.LENGTH_SHORT).show()
                    },
                    onAddAll = {
                        val online = (vm.reachMap[s.name] ?: emptyList()).filter { it.online }
                        online.forEach { AppRepo.addServer(buildSuggestedServer(s.name, it.url)) }
                        Toast.makeText(ctx, "Added ${online.size} server${if (online.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                    },
                )
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
private fun IspCard(
    server: IspServer,
    reach: List<UrlReach>?,
    expanded: Boolean,
    probing: Boolean,
    onToggle: () -> Unit,
    onBrowse: (url: String) -> Unit,
    onAdd: (url: String) -> Unit,
    onAddAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Dns, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(prettyIspName(server.name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(0.dp, 2.dp))
                    CardSummary(server = server, reach = reach, probing = probing)
                }
            }

            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    server.urls.forEach { url ->
                        val r = reach?.firstOrNull { it.url == url }
                        UrlRow(
                            url = url,
                            online = r?.online,
                            checking = reach == null,
                            onBrowse = { onBrowse(url) },
                            onAdd = { onAdd(url) },
                        )
                    }
                    val onlineCount = reach?.count { it.online } ?: 0
                    Button(
                        onClick = onAddAll,
                        enabled = onlineCount > 0,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(if (onlineCount > 0) "Add to server list ($onlineCount accessible)" else "No accessible address") }
                }
            }
        }
    }
}

@Composable
private fun CardSummary(server: IspServer, reach: List<UrlReach>?, probing: Boolean) {
    when {
        probing && reach == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            Text("Testing…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        reach != null -> {
            val ok = reach.count { it.online }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (ok > 0) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    tint = if (ok > 0) ONLINE else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(15.dp),
                )
                Text("$ok of ${reach.size} accessible", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> Text("${server.urls.size} address${if (server.urls.size == 1) "" else "es"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UrlRow(url: String, online: Boolean?, checking: Boolean, onBrowse: () -> Unit, onAdd: () -> Unit) {
    val enabled = online == true
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReachDot(online = online, checking = checking)
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBrowse, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(16.dp))
                Text("  Browse")
            }
            FilledTonalButton(onClick = onAdd, enabled = enabled, modifier = Modifier.weight(1f)) {
                Text("Add to server")
            }
        }
    }
}

@Composable
private fun ReachDot(online: Boolean?, checking: Boolean) {
    when {
        checking -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
        online == true -> Box(Modifier.size(9.dp).clip(CircleShape).background(ONLINE))
        else -> Box(Modifier.size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline))
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
