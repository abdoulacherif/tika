package com.abdoula.screenrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

// Vérifie une fois par jour si ça fait longtemps que l'utilisateur n'a pas
// enregistré, et envoie une notification discrète si c'est le cas.
// Désactivable dans les Réglages.
class InactivityReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        if (!SettingsManager.areRemindersEnabled(context)) return Result.success()

        val lastRecording = SettingsManager.getLastRecordingTime(context)
        if (lastRecording == 0L) return Result.success() // Jamais enregistré : pas de rappel

        val daysSince = (System.currentTimeMillis() - lastRecording) / 86_400_000L
        if (daysSince < 5) return Result.success()

        showReminderNotification(context)
        return Result.success()
    }

    private fun showReminderNotification(context: Context) {
        val channelId = "inactivity_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Rappels", NotificationManager.IMPORTANCE_LOW
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Ça fait un moment ! 👋")
            .setContentText("Tu n'as pas enregistré depuis quelques jours — un tuto à faire ?")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(888, notification)
    }
}