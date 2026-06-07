package xyz.devnerd.anmediaplayer.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.devnerd.anmediaplayer.ui.theme.Accent

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setAccent(accent: Accent) = viewModelScope.launch { repo.setAccent(accent) }
    fun setBrowseView(view: BrowseView) = viewModelScope.launch { repo.setBrowseView(view) }
    fun setNavPattern(pattern: NavPattern) = viewModelScope.launch { repo.setNavPattern(pattern) }
    fun setPlayerLayout(layout: PlayerLayout) = viewModelScope.launch { repo.setPlayerLayout(layout) }
    fun setResumeBehavior(behavior: ResumeBehavior) = viewModelScope.launch { repo.setResumeBehavior(behavior) }
    fun setAutoPlayNext(on: Boolean) = viewModelScope.launch { repo.setAutoPlayNext(on) }
    fun setSubtitlesDefault(on: Boolean) = viewModelScope.launch { repo.setSubtitlesDefault(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(on) }
    fun setWifiOnly(on: Boolean) = viewModelScope.launch { repo.setWifiOnly(on) }
    fun setAppLock(on: Boolean) = viewModelScope.launch { repo.setAppLock(on) }
    fun setDownloadDir(uri: String?) = viewModelScope.launch { repo.setDownloadDir(uri) }
}
