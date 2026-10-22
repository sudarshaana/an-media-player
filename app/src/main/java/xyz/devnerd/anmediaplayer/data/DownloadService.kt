package xyz.devnerd.anmediaplayer.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
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
        private const val CHANNEL_DONE = "downloads_done"
        private const val NOTIF_ID = 4201
        const val ACTION_PAUSE_ALL = "xyz.devnerd.anmediaplayer.action.PAUSE_ALL"
        const val ACTION_RETRY = "xyz.devnerd.anmediaplayer.action.RETRY"
        const val EXTRA_ID = "id"

        /** True between onCreate/onDestroy — a late progress tick must not re-post the ongoing notification. */
        @Volatile var running = false
            private set

        private fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
            if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        private fun openApp(ctx: Context): PendingIntent? {
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun action(ctx: Context, action: String, id: String? = null): PendingIntent {
            val i = Intent(ctx, DownloadActionReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
            return PendingIntent.getBroadcast(ctx, (action + id).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun resultNotifId(id: String) = id.hashCode()

        fun notifyProgress(ctx: Context, title: String, text: String, progress: Int, indeterminate: Boolean, pauseLabel: String) {
            if (!running) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progress.coerceIn(0, 100), indeterminate)
                .setContentIntent(openApp(ctx))
                .addAction(0, pauseLabel, action(ctx, ACTION_PAUSE_ALL))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            runCatching { nm.notify(NOTIF_ID, notif) }
        }

        /** One dismissable notification per finished/failed download; failed ones carry a Retry action. */
        fun notifyResult(ctx: Context, d: Download, ok: Boolean) {
            ensureChannels(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val b = NotificationCompat.Builder(ctx, CHANNEL_DONE)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (ok) "Download complete" else "Download failed")
                .setContentText(listOf(d.title, d.sub).filter { it.isNotBlank() && it != d.file }.joinToString(" · ").ifBlank { d.file })
                .setContentIntent(openApp(ctx))
                .setAutoCancel(true)
            if (!ok) b.addAction(0, "Retry", action(ctx, ACTION_RETRY, d.id))
            runCatching { nm.notify(resultNotifId(d.id), b.build()) }
        }

        fun cancelResult(ctx: Context, id: String) {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(resultNotifId(id))
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        ensureChannels(this)
        val initial = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentIntent(openApp(this))
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, initial)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}

/** Handles notification actions (Pause / Retry) without bringing the app to the foreground. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadService.ACTION_PAUSE_ALL -> DownloadsStore.pauseAll()
            DownloadService.ACTION_RETRY -> intent.getStringExtra(DownloadService.EXTRA_ID)?.let { DownloadsStore.resume(it) }
        }
    }
}
