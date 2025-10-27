// android/app/src/main/kotlin/com/example/webrtc_screen_share/MediaProjectionService.kt
package com.example.webrtc_screen_share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.app.PendingIntent
import android.util.Log
import androidx.core.app.NotificationCompat

class MediaProjectionService : Service() {
    private val CHANNEL_ID = "media_projection_channel"
    private val NOTIF_ID = 789

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        // start as foreground service with type 'mediaProjection' declared in manifest
        startForeground(NOTIF_ID, notification)
        Log.d("MediaProjectionService", "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Here you could create the MediaProjection using MainActivity.projectionResultCode/data
        // and start a VirtualDisplay and hook into WebRTC native APIs.
        // For now, we just keep the service alive while Dart/Plugin uses MediaProjection.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // tidy up MediaProjection if you started one
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen share", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent: PendingIntent? = null
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen sharing active")
            .setContentText("Your screen is being shared")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .build()
    }
}
