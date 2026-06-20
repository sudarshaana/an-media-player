package xyz.devnerd.anmediaplayer.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps [DownloadsStore]'s transfer coroutines exempt from background
 * process freezing/network throttling (esp. Samsung OneUI) — without it, backgrounding the app
 * (not killing it) stalls the in-flight socket read and the download fails.
 */
class DownloadService : Service() {
    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 4201

        fun notify(ctx: Context, title: String, text: String, progress: Int) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progress.coerceIn(0, 100), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            runCatching { nm.notify(NOTIF_ID, notif) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val initial = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, initial)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
