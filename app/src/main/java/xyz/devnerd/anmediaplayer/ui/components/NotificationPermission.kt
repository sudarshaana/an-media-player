package xyz.devnerd.anmediaplayer.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a callback that asks for POST_NOTIFICATIONS (Android 13+) if not yet granted — call it when the
 * user starts a download, so progress/completion notifications can show. Downloads run either way.
 */
@Composable
fun rememberNotificationPermissionAsker(): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return remember(launcher) {
        {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
