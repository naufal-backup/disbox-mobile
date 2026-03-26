package com.disbox.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player

class NotificationHelper(private val context: Context) {
    private val CHANNEL_ID = "disbox_transfers"
    private val MEDIA_CHANNEL_ID = "disbox_media"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val transferChannel = NotificationChannel(CHANNEL_ID, "File Transfers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifications for upload and download progress"
            }
            val mediaChannel = NotificationChannel(MEDIA_CHANNEL_ID, "Media Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifications for music playback"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(transferChannel)
            notificationManager.createNotificationChannel(mediaChannel)
        }
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
    }

    fun showMediaNotification(title: String, isPlaying: Boolean, albumArt: Bitmap? = null) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, MEDIA_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .setPriority(NotificationCompat.PRIORITY_LOW)

        notificationManager.notify(1337, builder.build())
    }

    fun cancelMediaNotification() {
        notificationManager.cancel(1337)
    }
}
