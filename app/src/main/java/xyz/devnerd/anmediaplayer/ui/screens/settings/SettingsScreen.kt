package xyz.devnerd.anmediaplayer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import xyz.devnerd.anmediaplayer.settings.AppSettings
import xyz.devnerd.anmediaplayer.settings.BrowseView
import xyz.devnerd.anmediaplayer.settings.NavPattern
import xyz.devnerd.anmediaplayer.settings.PlayerLayout
import xyz.devnerd.anmediaplayer.settings.ResumeBehavior
import xyz.devnerd.anmediaplayer.settings.ThemeMode
import xyz.devnerd.anmediaplayer.ui.theme.Accent

/** All Settings mutations in one place. */
data class SettingsActions(
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onAccent: (Accent) -> Unit = {},
    val onBrowseView: (BrowseView) -> Unit = {},
    val onNavPattern: (NavPattern) -> Unit = {},
    val onPlayerLayout: (PlayerLayout) -> Unit = {},
    val onResumeBehavior: (ResumeBehavior) -> Unit = {},
    val onAutoPlayNext: (Boolean) -> Unit = {},
    val onSubtitlesDefault: (Boolean) -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onAppLock: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, actions: SettingsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Settings") }, windowInsets = androidx.compose.foundation.layout.WindowInsets(0)) },
    ) { inner ->
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = inner.calculateTopPadding(), bottom = 32.dp)) {
            item {
                SettingGroup("APPEARANCE") {
                    RowBelow(Icons.Outlined.DarkMode, "Theme") {
                        Seg(ThemeMode.entries.map { it to it.label }, settings.themeMode, actions.onThemeMode, Modifier.fillMaxWidth())
                    }
                    Div()
                    RowBelow(Icons.Outlined.Lightbulb, "Accent") {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Accent.entries.forEach { acc -> AccentSwatch(acc.swatch, acc == settings.accent) { actions.onAccent(acc) } }
                        }
                    }
                    Div()
                    RowInline(Icons.Outlined.GridView, "Default browse view") {
                        Seg(BrowseView.entries.map { it to it.label }, settings.browseView, actions.onBrowseView)
                    }
                    Div()
                    RowInline(Icons.Outlined.Link, "Navigation", "Folder path display") {
                        Seg(listOf(NavPattern.BREADCRUMB to "Path", NavPattern.BACKSTACK to "Stack"), settings.navPattern, actions.onNavPattern)
                    }
                }
            }
            item {
                SettingGroup("PLAYBACK") {
                    RowBelow(Icons.Outlined.PlayCircle, "Player layout") {
                        Seg(PlayerLayout.entries.map { it to it.label }, settings.playerLayout, actions.onPlayerLayout, Modifier.fillMaxWidth())
                    }
                    Div()
                    RowInline(Icons.Outlined.SkipNext, "Auto-play next", "Continue to the next file in a folder") {
                        Switch(checked = settings.autoPlayNext, onCheckedChange = actions.onAutoPlayNext)
                    }
                    Div()
                    RowInline(Icons.Outlined.History, "Resume playback", if (settings.resumeBehavior == ResumeBehavior.ASK) "Ask each time" else "Always resume") {
                        Seg(ResumeBehavior.entries.map { it to it.label }, settings.resumeBehavior, actions.onResumeBehavior)
                    }
                    Div()
                    RowInline(Icons.Outlined.ClosedCaption, "Subtitles on by default") {
                        Switch(checked = settings.subtitlesDefault, onCheckedChange = actions.onSubtitlesDefault)
                    }
                    Div()
                    RowInline(Icons.Outlined.Lightbulb, "Keep screen on", "During playback") {
                        Switch(checked = settings.keepScreenOn, onCheckedChange = actions.onKeepScreenOn)
                    }
                }
            }
            item {
                SettingGroup("DOWNLOADS & DATA") {
                    RowInline(Icons.Outlined.Wifi, "Download on Wi-Fi only") {
                        Switch(checked = settings.wifiOnly, onCheckedChange = actions.onWifiOnly)
                    }
                    Div()
                    RowInline(Icons.Outlined.Download, "Download location", "Internal storage · Movies/MediaBrowser") {
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SettingGroup("ABOUT") {
                    RowInline(Icons.Outlined.Info, "Player engine", "Media3 (ExoPlayer)") {}
                    Div()
                    RowInline(Icons.Outlined.Storage, "Version", appVersion()) {}
                }
            }
        }
    }
}

@Composable
private fun SettingGroup(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column { content() }
    }
    Spacer(Modifier.size(10.dp))
}

@Composable
private fun RowInline(icon: ImageVector, title: String, sub: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun RowBelow(icon: ImageVector, title: String, sub: String? = null, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp)) { control() }
    }
}

@Composable
private fun Div() = HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Seg(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
        }
    }
}

@Composable
private fun appVersion(): String {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember {
        runCatching {
            @Suppress("DEPRECATION")
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (build ${pi.versionCode})"
        }.getOrDefault("1.0")
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).clip(CircleShape).background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
