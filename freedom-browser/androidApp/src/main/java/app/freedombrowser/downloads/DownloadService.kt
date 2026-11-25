package app.freedombrowser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.freedombrowser.FreedomApp
import app.freedombrowser.MainActivity
import app.freedombrowser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while downloads run (foreground service, type dataSync) and shows a
 * single progress notification. Stops itself when no download is active.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(getString(R.string.downloads_running), null, 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (observer == null) {
            val controller = (application as FreedomApp).container.downloads
            observer = scope.launch {
                combine(controller.downloads, controller.activeCount) { list, active -> list to active }.collectLatest { (list, active) ->
                    if (active <= 0) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@collectLatest
                    }
                    val running = list.filter { it.status.name == "RUNNING" || it.status.name == "QUEUED" }
                    val first = running.firstOrNull()
                    val total = running.sumOf { it.totalBytes.coerceAtLeast(0) }
                    val done = running.sumOf { it.downloadedBytes }
                    val title = if (running.size == 1) first?.fileName ?: getString(R.string.downloads_running)
                    else resources.getQuantityString(R.plurals.downloads_count, running.size, running.size)
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, build(title, first?.fileName, done, total))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, text: String?, done: Long, total: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_DOWNLOADS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val indeterminate = total <= 0
        val progress = if (indeterminate) 0 else ((done * 1000) / total).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(1000, progress, indeterminate)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4101

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.downloads_title), NotificationManager.IMPORTANCE_LOW))
            }
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
