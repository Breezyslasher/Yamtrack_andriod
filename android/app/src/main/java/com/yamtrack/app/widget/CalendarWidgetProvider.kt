package com.yamtrack.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yamtrack.app.MainActivity
import com.yamtrack.app.R
import com.yamtrack.app.ui.splash.SplashActivity

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

            // Tapping the title opens the app on the calendar tab.
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetTitle, openPending)

            // Tapping the arrow refreshes this widget's data. Broadcast to
            // ourselves with a unique URI so PendingIntents stay distinct
            // across multiple widget instances.
            val refreshIntent = Intent(context, CalendarWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse("yamtrack-refresh://widget/$id")
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, id, refreshIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPending)

            // Per-row click template — the factory fills in the media extras
            // per row. Routed through SplashActivity so the auth/token is
            // primed before MediaDetailsActivity loads.
            val rowIntent = Intent(context, SplashActivity::class.java)
            val rowPending = PendingIntent.getActivity(
                context, 1, rowIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setPendingIntentTemplate(R.id.widgetList, rowPending)

            appWidgetManager.updateAppWidget(id, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(id, R.id.widgetList)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return

        // Invalidate the on-disk events cache so the next onDataSetChanged
        // path goes straight to the network (no stale render).
        context.getSharedPreferences("widget_calendar_cache", Context.MODE_PRIVATE)
            .edit().remove("events_json").apply()

        val mgr = AppWidgetManager.getInstance(context)
        val targeted = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val ids = if (targeted != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intArrayOf(targeted)
        } else {
            mgr.getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
        }
        if (ids.isNotEmpty()) {
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.yamtrack.app.widget.ACTION_REFRESH"

        /** Force a refresh from anywhere in the app (e.g. after sign-in). */
        fun requestRefresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, CalendarWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
            }
        }
    }
}
