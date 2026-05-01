package com.yamtrack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yamtrack.app.MainActivity
import com.yamtrack.app.R

/**
 * Home-screen widget that lists upcoming calendar events from the user's
 * Yamtrack server. The list is rendered through a RemoteViewsService so
 * each row can include the per-event launch intent.
 */
class CalendarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)

            // Service that supplies the list rows.
            val intent = Intent(context, CalendarWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widgetList, intent)
            views.setEmptyView(R.id.widgetList, R.id.widgetEmpty)

            // Tapping the title bar opens the app on the calendar tab.
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPending)
            views.setOnClickPendingIntent(R.id.widgetRefresh, openPending)

            // Per-row click template — RemoteViewsFactory fills in the rest.
            val rowIntent = Intent(context, MainActivity::class.java)
            val rowPending = PendingIntent.getActivity(
                context, 0, rowIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widgetList, rowPending)

            appWidgetManager.updateAppWidget(id, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widgetList)
        }
    }

    companion object {
        /** Force a refresh from anywhere in the app (e.g. after sign-in). */
        fun requestRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, CalendarWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
            }
        }
    }
}
