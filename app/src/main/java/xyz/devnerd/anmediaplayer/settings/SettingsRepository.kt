package xyz.devnerd.anmediaplayer.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.devnerd.anmediaplayer.ui.theme.Accent

/** Three-way theme selection. Dark is the app's default look. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Default directory layout. */
enum class BrowseView(val key: String, val label: String) {
    LIST("list", "List"),
    GRID("grid", "Grid");

    companion object {
        fun fromKey(key: String?): BrowseView = entries.firstOrNull { it.key == key } ?: LIST
    }
}

/** Folder navigation style. */
enum class NavPattern(val key: String, val label: String) {
    BREADCRUMB("breadcrumb", "Breadcrumb"),
    BACKSTACK("backstack", "Back-stack");

    companion object {
        fun fromKey(key: String?): NavPattern = entries.firstOrNull { it.key == key } ?: BREADCRUMB
    }
}

/** Player control layout. */
enum class PlayerLayout(val key: String, val label: String) {
    STANDARD("standard", "Standard"),
    MINIMAL("minimal", "Minimal"),
    CINEMA("cinema", "Cinema");

    companion object {
        fun fromKey(key: String?): PlayerLayout = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/** What happens when reopening a partially-watched item. */
enum class ResumeBehavior(val key: String, val label: String) {
    ASK("ask", "Ask"),
    RESUME("resume", "Resume");

    companion object {
        fun fromKey(key: String?): ResumeBehavior = entries.firstOrNull { it.key == key } ?: ASK
    }
}

/** Resolved, persisted user preferences read at the app root. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.PURPLE,
    val browseView: BrowseView = BrowseView.LIST,
    val navPattern: NavPattern = NavPattern.BREADCRUMB,
    val playerLayout: PlayerLayout = PlayerLayout.STANDARD,
    val resumeBehavior: ResumeBehavior = ResumeBehavior.ASK,
    val autoPlayNext: Boolean = true,
    val subtitlesDefault: Boolean = false,
    val keepScreenOn: Boolean = true,
    val wifiOnly: Boolean = true,
    val appLock: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val BROWSE_VIEW = stringPreferencesKey("browse_view")
        val NAV_PATTERN = stringPreferencesKey("nav_pattern")
        val PLAYER_LAYOUT = stringPreferencesKey("player_layout")
        val RESUME_BEHAVIOR = stringPreferencesKey("resume_behavior")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val SUBTITLES_DEFAULT = booleanPreferencesKey("subtitles_default")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val APP_LOCK = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            accent = Accent.fromKey(prefs[Keys.ACCENT]),
            browseView = BrowseView.fromKey(prefs[Keys.BROWSE_VIEW]),
            navPattern = NavPattern.fromKey(prefs[Keys.NAV_PATTERN]),
            playerLayout = PlayerLayout.fromKey(prefs[Keys.PLAYER_LAYOUT]),
            resumeBehavior = ResumeBehavior.fromKey(prefs[Keys.RESUME_BEHAVIOR]),
            autoPlayNext = prefs[Keys.AUTO_PLAY_NEXT] ?: true,
            subtitlesDefault = prefs[Keys.SUBTITLES_DEFAULT] ?: false,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: true,
            appLock = prefs[Keys.APP_LOCK] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setAccent(accent: Accent) {
        context.dataStore.edit { it[Keys.ACCENT] = accent.key }
    }

    suspend fun setBrowseView(view: BrowseView) {
        context.dataStore.edit { it[Keys.BROWSE_VIEW] = view.key }
    }

    suspend fun setNavPattern(pattern: NavPattern) {
        context.dataStore.edit { it[Keys.NAV_PATTERN] = pattern.key }
    }

    suspend fun setPlayerLayout(layout: PlayerLayout) {
        context.dataStore.edit { it[Keys.PLAYER_LAYOUT] = layout.key }
    }

    suspend fun setResumeBehavior(behavior: ResumeBehavior) {
        context.dataStore.edit { it[Keys.RESUME_BEHAVIOR] = behavior.key }
    }

    suspend fun setAutoPlayNext(on: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = on }
    }

    suspend fun setSubtitlesDefault(on: Boolean) {
        context.dataStore.edit { it[Keys.SUBTITLES_DEFAULT] = on }
    }

    suspend fun setKeepScreenOn(on: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = on }
    }

    suspend fun setWifiOnly(on: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = on }
    }

    suspend fun setAppLock(on: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = on }
    }
}
