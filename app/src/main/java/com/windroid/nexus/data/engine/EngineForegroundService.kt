package com.windroid.nexus.data.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Keeps the emulation engine's child processes alive while the shell is in the
 * background (installing software, long-running builds). Declared with the
 * `specialUse` FGS type in the manifest. Binds to nothing; it exists purely to
 * hold a foreground promise so Android won't reap the process group.
 */
@AndroidEntryPoint
class EngineForegroundService : Service() {

    @Inject lateinit var engine: EngineBridge

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Sticky so the OS restarts us if memory pressure kills the service
        // while a guest process is still meant to be running.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Engine",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "WinDroid Nexus compatibility runtime" }
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WinDroid Nexus")
            .setContentText("Compatibility runtime is active")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "engine_fgs"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, EngineForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineForegroundService::class.java))
        }
    }
}
