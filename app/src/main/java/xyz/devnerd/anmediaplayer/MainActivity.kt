package xyz.devnerd.anmediaplayer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.devnerd.anmediaplayer.settings.SettingsViewModel
import xyz.devnerd.anmediaplayer.ui.App
import xyz.devnerd.anmediaplayer.ui.screens.settings.SettingsActions
import xyz.devnerd.anmediaplayer.ui.theme.AnMediaPlayerTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TVs are fixed-landscape — undo the manifest portrait lock on Leanback devices.
        if (isTvDevice()) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        enableEdgeToEdge()
        setContent {
            val vm: SettingsViewModel = viewModel()
            val settings by vm.settings.collectAsState()

            AnMediaPlayerTheme(themeMode = settings.themeMode, accent = settings.accent) {
                App(
                    settings = settings,
                    settingsActions = SettingsActions(
                        onThemeMode = vm::setThemeMode,
                        onAccent = vm::setAccent,
                        onBrowseView = vm::setBrowseView,
                        onNavPattern = vm::setNavPattern,
                        onPlayerLayout = vm::setPlayerLayout,
                        onResumeBehavior = vm::setResumeBehavior,
                        onAutoPlayNext = vm::setAutoPlayNext,
                        onSubtitlesDefault = vm::setSubtitlesDefault,
                        onKeepScreenOn = vm::setKeepScreenOn,
                        onWifiOnly = vm::setWifiOnly,
                        onAppLock = vm::setAppLock,
                        onDownloadDir = vm::setDownloadDir,
                        onSetSort = vm::setSort,
                        onBrowserTipsSeen = vm::setBrowserTipsSeen,
                    ),
                )
            }
        }
    }

    private fun isTvDevice(): Boolean {
        val ui = getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }
}
