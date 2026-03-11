package com.disbox.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class NotificationHelper(private val context: Context) {
    private val CHANNEL_ID = "disbox_transfers"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "File Transfers"
            val descriptionText = "Notifications for upload and download progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
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
        
        if (progress >= 1f) {
            // Optional: Dismiss after 3 seconds if completed
            // handler.postDelayed({ notificationManager.cancel(id) }, 3000)
        }
    }
}