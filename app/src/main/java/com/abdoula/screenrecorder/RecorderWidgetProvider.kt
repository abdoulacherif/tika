package com.abdoula.screenrecorder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.RemoteViews

class RecorderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_recorder)

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val count = dir?.listFiles { f -> f.extension == "mp4" }?.size ?: 0
            views.setTextViewText(R.id.widgetStats, "$count vidéo${if (count > 1) "s" else ""} enregistrée${if (count > 1) "s" else ""}")

            val startIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("autoStart", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetStartButton, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}