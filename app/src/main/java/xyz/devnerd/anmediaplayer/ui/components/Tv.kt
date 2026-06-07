package xyz.devnerd.anmediaplayer.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** True on Android TV / Leanback devices (no touch, D-pad navigation). */
fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Provided at the app root; read with `LocalIsTv.current` to branch TV-specific UI. */
val LocalIsTv = staticCompositionLocalOf { false }
