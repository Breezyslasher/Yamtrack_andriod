package com.yamtrack.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.episodes.EpisodesActivity
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
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

            // Match the app's calendar window: today through +3 months,
            // upcoming only. The server range can still include earlier
            // same-month entries, so filter to today-or-later as well.
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            val todayStr = iso.format(cal.time)
            val end = (cal.clone() as Calendar).apply { add(Calendar.MONTH, 3) }
            val endStr = iso.format(end.time)

            val result = repo.getCalendar(
                startDate = todayStr,
                endDate = endStr,
                limit = 200
            )
            if (result is Result.Success) {
                events += result.data
                    .filter { (it.date?.take(10) ?: "") >= todayStr }
                    .sortedBy { it.date.orEmpty() }
                    .take(30)
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

        val bitmap = event.image?.takeIf { it.isNotBlank() }?.let { loadBitmap(it) }
        if (bitmap != null) {
            views.setImageViewBitmap(R.id.widgetItemPoster, bitmap)
        } else {
            views.setImageViewResource(R.id.widgetItemPoster, R.drawable.placeholder_poster)
        }

        // Per-row deep link.
        val item = event.item
        if (item != null) {
            val isEpisodic = event.mediaType == MediaType.SEASON ||
                event.mediaType == MediaType.EPISODE
            val season = event.seasonNumber
            val fillIn = Intent().apply {
                putExtra(MediaDetailsActivity.EXTRA_SOURCE, item.source)
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, item.mediaId)
                if (isEpisodic && season != null) {
                    // Open that season's episode list directly.
                    putExtra(EpisodesActivity.EXTRA_SEASON, season)
                } else {
                    // Episodes/seasons aren't valid detail resources; if we
                    // can't resolve a season, fall back to the parent show.
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

    /** Tiny synchronous poster fetch — capped, off the main thread. */
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
}
