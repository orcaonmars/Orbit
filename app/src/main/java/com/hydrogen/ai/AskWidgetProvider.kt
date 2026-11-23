package com.hydrogen.ai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hydrogen.ai.model.QuickAction

/**
 * "Ask Orbit" home-screen widget: a search-bar style pill. Tapping the bar opens a fresh chat with the
 * keyboard up; the camera and mic buttons hand off exactly like the launcher shortcuts. The widget holds
 * no data and never sends anything by itself.
 */
class AskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_ask).apply {
            setOnClickPendingIntent(R.id.widget_ask_bar, launch(context, QuickAction.NEW_CHAT))
            setOnClickPendingIntent(R.id.widget_ask_photo, launch(context, QuickAction.ASK_PHOTO))
            setOnClickPendingIntent(R.id.widget_ask_voice, launch(context, QuickAction.VOICE))
        }
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    private fun launch(context: Context, action: QuickAction): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action.intentAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
