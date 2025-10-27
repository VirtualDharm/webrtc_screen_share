package com.example.webrtc_screen_share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MediaProjectionService : Service() {
    private var mediaProjection: MediaProjection? = null
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "MediaProjectionChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Retrieve the MediaProjection from the intent
        if (intent != null) {
            val resultCode = intent.getIntExtra("resultCode", RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>("data")
            if (resultCode == RESULT_OK && data != null) {
                val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        stopForeground(true)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Projection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Sharing")
            .setContentText("Screen sharing is active")
            .setSmallIcon(android.R.drawable.ic_menu_share) // Replace with your app's icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}