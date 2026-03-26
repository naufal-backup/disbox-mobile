package com.disbox.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.disbox.mobile.R

class NotificationHelper(private val context: Context) {
    private val CHANNEL_ID = "disbox_transfers"
    private val MEDIA_CHANNEL_ID = "disbox_media"
    private val MEDIA_NOTIFICATION_ID = 453
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transfersChannel = NotificationChannel(CHANNEL_ID, "File Transfers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifications for upload and download progress"
            }
            
            val mediaChannel = NotificationChannel(MEDIA_CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for current music playback"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            
            notificationManager.createNotificationChannel(transfersChannel)
            notificationManager.createNotificationChannel(mediaChannel)
        }
    }

    fun showMediaNotification(title: String, isPlaying: Boolean, albumArt: android.graphics.Bitmap? = null) {
        val builder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Disbox Music")
            .setContentText(title)
            .setLargeIcon(albumArt)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())

        notificationManager.notify(MEDIA_NOTIFICATION_ID, builder.build())
    }

    fun cancelMediaNotification() {
        notificationManager.cancel(MEDIA_NOTIFICATION_ID)
    }

    fun showProgressNotification(id: Int, title: String, progress: Float, isUpload: Boolean) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isUpload) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (progress >= 1f) "Completed" else "${(progress * 100).toInt()}%")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(progress < 1f)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOnlyAlertOnce(true)

        notificationManager.notify(id, builder.build())
        
        if (progress >= 1f) {
            // Optional: Dismiss after 3 seconds if completed
            // handler.postDelayed({ notificationManager.cancel(id) }, 3000)
        }
    }
}