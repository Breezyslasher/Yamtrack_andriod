package com.yamtrack.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RemoteViewsService that backs the calendar widget. Hilt's component
 * lifecycle doesn't directly inject services (there's no @AndroidEntryPoint
 * for RemoteViewsService), so we pull dependencies through an EntryPoint.
 */
class CalendarWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun repository(): YamtrackRepository
        fun preferences(): PreferencesManager
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CalendarRemoteViewsFactory(applicationContext)
}

private class CalendarRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private val events = mutableListOf<CalendarEvent>()
    private val isoParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayFmt = SimpleDateFormat("d", Locale.getDefault())
    private val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())

    private val entryPoint: CalendarWidgetService.WidgetEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            context,
            CalendarWidgetService.WidgetEntryPoint::class.java
        )
    }

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        // Called on every notifyAppWidgetViewDataChanged. Block here is fine —
        // RemoteViewsFactory is invoked off the main thread by the system.
        events.clear()
        runBlocking {
            val prefs = entryPoint.preferences()
            val token = prefs.apiToken.first()
            val server = prefs.serverUrl.first()
            if (token.isNullOrBlank()) return@runBlocking

            val repo = entryPoint.repository()
            repo.setServerUrl(server)
            repo.setToken(token)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            // Fetch a wider window than the default month so the widget can
            // show upcoming items for users with sparse releases.
            val result = repo.getCalendar(startDate = today, limit = 50)
            if (result is Result.Success) {
                events += result.data.sortedBy { it.date.orEmpty() }.take(20)
            }
        }
    }

    override fun onDestroy() {
        events.clear()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_calendar_item)
        val event = events[position]

        val dateStr = event.date?.let { it.substring(0, minOf(10, it.length)) }
        val parsed = dateStr?.let { runCatching { isoParser.parse(it) }.getOrNull() }
        if (parsed != null) {
            views.setTextViewText(R.id.widgetItemDay, dayFmt.format(parsed))
            views.setTextViewText(R.id.widgetItemMonth, monthFmt.format(parsed))
        } else {
            views.setTextViewText(R.id.widgetItemDay, "?")
            views.setTextViewText(R.id.widgetItemMonth, "")
        }

        views.setTextViewText(R.id.widgetItemTitle, event.title.ifBlank { "—" })
        views.setTextViewText(R.id.widgetItemDetail, buildDetailLine(event))

        // Click intent sent to MainActivity (PendingIntent template was set
        // in the provider). We just attach a no-op fillInIntent so the
        // template fires when the row is tapped.
        views.setOnClickFillInIntent(R.id.widgetItemRoot, Intent())
        return views
    }

    private fun buildDetailLine(event: CalendarEvent): String {
        val typeLabel = event.mediaType?.displayName.orEmpty()
        val parts = mutableListOf<String>()
        if (typeLabel.isNotBlank()) parts.add(typeLabel)
        event.seasonNumber?.let { parts.add("S$it") }
        event.episodeNumber?.let { parts.add("E$it") }
            ?: event.contentNumber?.let { parts.add("#$it") }
        return parts.joinToString(" · ")
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = events[position].id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
