package com.yamtrack.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.episodes.EpisodesActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * RemoteViewsService that backs the calendar widget. Hilt's component
 * lifecycle doesn't directly inject services (no @AndroidEntryPoint for
 * RemoteViewsService), so we pull dependencies through an EntryPoint.
 */
class CalendarWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun repository(): YamtrackRepository
        fun preferences(): PreferencesManager
        fun moshi(): Moshi
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return CalendarRemoteViewsFactory(applicationContext, widgetId)
    }
}

/** Shared CoroutineScope so the factory can refresh in the background
 *  without blocking onDataSetChanged. */
private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private class CalendarRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
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

    private val cachePrefs by lazy {
        context.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE)
    }

    private val cacheAdapter: JsonAdapter<List<CalendarEvent>> by lazy {
        entryPoint.moshi().adapter(
            Types.newParameterizedType(List::class.java, CalendarEvent::class.java)
        )
    }

    override fun onCreate() = Unit

    /**
     * Show the cached events instantly (zero-network path); kick off a
     * background refresh that only re-renders the widget when the fetched
     * list actually differs from what's cached.
     */
    override fun onDataSetChanged() {
        events.clear()
        events += loadFromCache()

        widgetScope.launch {
            val fresh = fetchEvents() ?: return@launch
            val freshJson = cacheAdapter.toJson(fresh)
            val cachedJson = cachePrefs.getString(KEY_EVENTS, null)
            if (freshJson == cachedJson) return@launch
            cachePrefs.edit().putString(KEY_EVENTS, freshJson).apply()
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                AppWidgetManager.getInstance(context)
                    .notifyAppWidgetViewDataChanged(widgetId, R.id.widgetList)
            } else {
                // Provider didn't pass an id (shouldn't happen) — broadcast
                // to every active calendar widget instead.
                val mgr = AppWidgetManager.getInstance(context)
                val all = mgr.getAppWidgetIds(
                    ComponentName(context, CalendarWidgetProvider::class.java)
                )
                if (all.isNotEmpty()) {
                    mgr.notifyAppWidgetViewDataChanged(all, R.id.widgetList)
                }
            }
        }

        // If there's no cache yet, do a blocking fetch so the very first
        // render isn't empty.
        if (events.isEmpty()) {
            runBlocking {
                fetchEvents()?.let {
                    events += it
                    cachePrefs.edit()
                        .putString(KEY_EVENTS, cacheAdapter.toJson(it))
                        .apply()
                }
            }
        }
    }

    private fun loadFromCache(): List<CalendarEvent> {
        val json = cachePrefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching { cacheAdapter.fromJson(json) }.getOrNull().orEmpty()
    }

    /** Today through +3 months, upcoming only, top 30. */
    private suspend fun fetchEvents(): List<CalendarEvent>? {
        val prefs = entryPoint.preferences()
        val token = prefs.apiToken.first()
        val server = prefs.serverUrl.first()
        if (token.isNullOrBlank()) return null

        val repo = entryPoint.repository()
        repo.setServerUrl(server)
        repo.setToken(token)

        val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val todayStr = iso.format(cal.time)
        val end = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 3) }
        val endStr = iso.format(end.time)

        val result = repo.getCalendar(
            startDate = todayStr, endDate = endStr, limit = 200
        )
        return (result as? Result.Success)?.data
            ?.filter { (it.date?.take(10) ?: "") >= todayStr }
            ?.sortedBy { it.date.orEmpty() }
            ?.take(30)
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

        val bitmap = event.image?.takeIf { it.isNotBlank() }?.let { loadBitmap(it) }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widgetItemPoster, bitmap)
        } else {
            views.setImageViewResource(R.id.widgetItemPoster, R.drawable.placeholder_poster)
        }

        val item = event.item
        if (item != null) {
            val isEpisodic = event.mediaType == MediaType.SEASON ||
                event.mediaType == MediaType.EPISODE
            val season = event.seasonNumber
            val fillIn = Intent().apply {
                putExtra(MediaDetailsActivity.EXTRA_SOURCE, item.source)
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, item.mediaId)
                if (isEpisodic && season != null) {
                    putExtra(EpisodesActivity.EXTRA_SEASON, season)
                } else {
                    val type = if (isEpisodic) MediaType.TV.value else item.mediaType
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type)
                }
            }
            views.setOnClickFillInIntent(R.id.widgetItemRoot, fillIn)
        } else {
            views.setOnClickFillInIntent(R.id.widgetItemRoot, Intent())
        }
        return views
    }

    private fun loadBitmap(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    }.getOrNull()

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

    companion object {
        private const val CACHE_FILE = "widget_calendar_cache"
        private const val KEY_EVENTS = "events_json"
    }
}
