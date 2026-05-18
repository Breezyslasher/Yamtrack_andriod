package com.yamtrack.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * Media types that can have a *parent* tracked entry.
 * Matches `MEDIA_TYPE_VALID_LIST` in api/helpers.py.
 *
 * The "complete" set adds SEASON and EPISODE which are children of TV.
 */
enum class MediaType(val value: String, val displayName: String) {
    MOVIE("movie", "Movies"),
    TV("tv", "TV Shows"),
    SEASON("season", "Seasons"),
    EPISODE("episode", "Episodes"),
    ANIME("anime", "Anime"),
    MANGA("manga", "Manga"),
    GAME("game", "Games"),
    BOOK("book", "Books"),
    COMIC("comic", "Comics"),
    BOARDGAME("boardgame", "Board Games");

    companion object {
        fun fromValue(value: String?): MediaType? = values().find { it.value == value }
        /** Top-level types that show in user-facing filters. Excludes seasons & episodes. */
        val parentTypes: List<MediaType> = listOf(MOVIE, TV, ANIME, MANGA, GAME, BOOK, COMIC, BOARDGAME)
    }
}

/**
 * Media tracking statuses.
 *
 * INPUT direction (query params, POST/PATCH bodies): the server's
 * `parse_status_param` does `int(status)`, then maps to its canonical name
 * via MEDIA_STATUS_MAP. So the integer code is what we send to the API.
 *
 * OUTPUT direction (JSON responses): the server emits the canonical *name*
 * ("Planning", "In progress", "Paused", "Completed", "Dropped") via
 * StatusField.to_representation. So we parse responses by name.
 *
 * The `apiName` constant is the exact spelling the server returns/expects
 * — do not touch the casing/spacing.
 */
enum class MediaStatus(val code: Int, val apiName: String, val displayName: String) {
    PLANNING(0, "Planning", "Planning"),
    IN_PROGRESS(1, "In progress", "In Progress"),
    PAUSED(2, "Paused", "Paused"),
    COMPLETED(3, "Completed", "Completed"),
    DROPPED(4, "Dropped", "Dropped");

    companion object {
        fun fromCode(code: Int?): MediaStatus? = values().find { it.code == code }

        /**
         * Match a status returned by the API.
         * Accepts the canonical name (preferred) or a numeric code-as-string
         * for forward-compatibility.
         */
        fun fromApi(value: String?): MediaStatus? {
            if (value.isNullOrBlank()) return null
            values().find { it.apiName.equals(value, ignoreCase = true) }?.let { return it }
            return value.toIntOrNull()?.let { code -> values().find { it.code == code } }
        }

        /** Pseudo-status for "no filter" — sent as empty string. */
        const val ALL_FILTER_VALUE = ""
    }
}

/**
 * Source/provider for an item, e.g. tmdb, mal, igdb, manual.
 * From VALID_SOURCES in api/helpers.py.
 */
object Sources {
    const val TMDB = "tmdb"
    const val MAL = "mal"
    const val MANGAUPDATES = "mangaupdates"
    const val IGDB = "igdb"
    const val OPENLIBRARY = "openlibrary"
    const val HARDCOVER = "hardcover"
    const val COMICVINE = "comicvine"
    const val BGG = "bgg"
    const val MANUAL = "manual"
}

/**
 * Item metadata. Mirrors the Item Django model returned by ItemSerializer.
 */
data class Item(
    @SerializedName("media_id") val mediaId: String,
    @SerializedName("source") val source: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("season_number") val seasonNumber: Int? = null,
    @SerializedName("episode_number") val episodeNumber: Int? = null
)

/**
 * Membership entry returned in the `lists` array of MediaItem.
 * Format from CustomListItem.objects.get_user_item_lists.
 */
data class MediaListMembership(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("name") val name: String? = null
)

/**
 * Tracked media entry. Returned by:
 *   GET /api/v1/media/
 *   GET /api/v1/media/{media_type}/
 * via MediaSerializer.to_representation().
 */
data class MediaItem(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("consumption_id") val consumptionId: Long? = null,
    @SerializedName("item") val item: Item? = null,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    @SerializedName("tracked") val tracked: Boolean = false,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("progressed_at") val progressedAt: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("lists") val lists: List<MediaListMembership>? = null
) {
    val mediaType: MediaType? get() = item?.mediaType?.let { MediaType.fromValue(it) }
    val mediaStatus: MediaStatus? get() = MediaStatus.fromApi(status)
    val title: String get() = item?.title.orEmpty()
    val image: String? get() = item?.image
    val mediaId: String get() = item?.mediaId.orEmpty()
    val source: String get() = item?.source.orEmpty()
}

/**
 * Detail response. Returned by:
 *   GET /api/v1/media/{media_type}/{source}/{media_id}/
 *   PATCH /api/v1/media/{media_type}/{source}/{media_id}/
 * via CompleteMediaSerializer. Contains rich metadata from the upstream provider
 * plus user tracking data.
 */
data class MediaDetails(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("media_id") val mediaId: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("source_url") val sourceUrl: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("max_progress") val maxProgress: Int? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("synopsis") val synopsis: String? = null,
    @SerializedName("genres") val genres: List<String>? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("score_count") val scoreCount: Int? = null,
    @SerializedName("details") val details: Map<String, Any?>? = null,
    @SerializedName("related") val related: Map<String, Any?>? = null,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    @SerializedName("tracked") val tracked: Boolean = false,
    @SerializedName("consumptions_number") val consumptionsNumber: Int = 0,
    @SerializedName("consumptions") val consumptions: List<HistoryEntry>? = null,
    @SerializedName("lists") val lists: List<MediaListMembership>? = null
) {
    val type: MediaType? get() = MediaType.fromValue(mediaType)

    /** First consumption entry contains the user's tracking state. */
    val userConsumption: HistoryEntry? get() = consumptions?.firstOrNull()
    val userStatus: MediaStatus? get() = MediaStatus.fromApi(userConsumption?.status)
    val userScore: Double? get() = userConsumption?.score
    val userProgress: Int? get() = userConsumption?.progress
    val userNotes: String? get() = userConsumption?.notes

    /**
     * Human label for when the title aired/released. Provider metadata keys
     * vary (TMDB/MAL/IGDB/…) and arrive in the opaque `details` map, so we
     * probe a range of common keys and collapse to "2011" or "2011 - 2013".
     */
    val releaseLabel: String? get() = MediaMeta.releaseLabel(details)

    /** Episode/movie length in minutes if the provider exposed it. */
    val runtimeLabel: String? get() = MediaMeta.runtimeLabel(details)

    /** When the user last logged progress for this item. */
    val lastWatched: String? get() =
        (userConsumption?.endDate ?: userConsumption?.progressedAt
            ?: userConsumption?.created)?.take(10)
}

/** Defensive readers over the provider-passthrough `details` map. */
object MediaMeta {
    private fun str(v: Any?): String? = when (v) {
        is String -> v.takeIf { it.isNotBlank() }
        is Number -> v.toInt().toString()
        else -> null
    }

    private fun year(v: Any?): String? = str(v)?.let { s ->
        Regex("""\d{4}""").find(s)?.value
    }

    fun releaseLabel(details: Map<String, Any?>?): String? {
        if (details == null) return null
        val start = year(details["start_year"]) ?: year(details["first_air_date"])
            ?: year(details["release_date"]) ?: year(details["air_date"])
            ?: year(details["start_date"]) ?: year(details["year"])
        val end = year(details["end_year"]) ?: year(details["last_air_date"])
            ?: year(details["end_date"])
        return when {
            start == null -> null
            end == null || end == start -> start
            else -> "$start - $end"
        }
    }

    fun releaseDate(details: Map<String, Any?>?): String? {
        if (details == null) return null
        return listOf("air_date", "release_date", "first_air_date", "start_date")
            .firstNotNullOfOrNull { str(details[it]) }
    }

    fun runtimeLabel(details: Map<String, Any?>?): String? {
        if (details == null) return null
        val mins = listOf("runtime", "length", "duration", "episode_runtime")
            .firstNotNullOfOrNull { details[it] }
        val n = when (mins) {
            is Number -> mins.toInt()
            is String -> mins.trim().toIntOrNull()
            is List<*> -> (mins.firstOrNull() as? Number)?.toInt()
            else -> null
        } ?: return null
        return "$n min"
    }
}

/**
 * History/consumption entry. From HistorySerializer.
 */
data class HistoryEntry(
    @SerializedName("consumption_id") val consumptionId: Long? = null,
    @SerializedName("created") val created: String? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("progressed_at") val progressedAt: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * Search-provider hit. The Yamtrack search endpoint passes through whatever
 * the upstream provider returns inside `results`, so this model captures
 * the *common* fields the app actually uses.
 *
 * Search providers (TMDB, MAL, IGDB, …) all return at least: media_id, source,
 * media_type, title, image. Everything else may or may not be present.
 */
data class SearchResult(
    @SerializedName("media_id") val mediaId: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("media_type") val mediaType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("synopsis") val synopsis: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("tracked") val tracked: Boolean = false,
    @SerializedName("item_id") val itemId: String? = null
) {
    val type: MediaType? get() = MediaType.fromValue(mediaType)
    val displayTitle: String get() = title ?: "Unknown"
}

/**
 * Pagination wrapper used by every list endpoint.
 * From paginate_data() in api/helpers.py.
 */
data class PaginatedResponse<T>(
    @SerializedName("pagination") val pagination: Pagination,
    @SerializedName("results") val results: List<T>
)

data class Pagination(
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("next") val next: String? = null,
    @SerializedName("previous") val previous: String? = null
) {
    val hasNext: Boolean get() = next != null
    val hasPrevious: Boolean get() = previous != null
}

/**
 * Server `info` endpoint response. From InfoSerializer.
 */
data class AppInfo(
    @SerializedName("version") val version: String? = null,
    @SerializedName("debug") val debug: Boolean? = null,
    @SerializedName("frontend_url") val frontendUrl: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
    @SerializedName("admin_enabled") val adminEnabled: Boolean? = null,
    @SerializedName("track_time") val trackTime: Boolean? = null
)

/**
 * Health check response from HealthResponseSerializer.
 *   {status: "ok"|"unavailable", timestamp: ISO8601, checks: {plugin: {status, error}}}
 */
data class HealthResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("checks") val checks: Map<String, HealthCheck>? = null
) {
    val isHealthy: Boolean get() = status == "ok"
}

data class HealthCheck(
    @SerializedName("status") val status: String? = null,
    @SerializedName("error") val error: String? = null
)

/**
 * Statistics endpoint response. The shape is defined inline in StatisticsView.get():
 *   {
 *     start_date, end_date,
 *     media_count: { tv, movie, anime, ... },
 *     activity_data,
 *     media_type_distribution,
 *     score_distribution,
 *     top_rated: [MediaItem],
 *     status_distribution,
 *     status_pie_chart_data,
 *     timeline: { "2025-01": [TimelineItem], ... }
 *   }
 *
 * Most fields are intentionally typed as Map<String, Any?> because they hold
 * chart-ready data that the app re-shapes on demand. Use the typed convenience
 * accessors when possible.
 */
data class UserStats(
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("media_count") val mediaCount: Map<String, Any?>? = null,
    @SerializedName("activity_data") val activityData: Map<String, Any?>? = null,
    @SerializedName("media_type_distribution") val mediaTypeDistribution: Map<String, Any?>? = null,
    @SerializedName("score_distribution") val scoreDistribution: Map<String, Any?>? = null,
    @SerializedName("top_rated") val topRated: List<Map<String, Any?>>? = null,
    @SerializedName("status_distribution") val statusDistribution: Map<String, Any?>? = null,
    @SerializedName("status_pie_chart_data") val statusPieChartData: Map<String, Any?>? = null,
    @SerializedName("timeline") val timeline: Map<String, Any?>? = null
) {
    /** Total tracked items (sum of every media type). */
    val total: Int get() = mediaCount?.values?.filterIsInstance<Number>()?.sumOf { it.toInt() } ?: 0

    fun countFor(mediaType: MediaType): Int =
        (mediaCount?.get(mediaType.value) as? Number)?.toInt() ?: 0

    /**
     * Status counts derived from status_distribution. The exact shape of
     * status_distribution depends on the server but typically holds totals.
     */
    val completed: Int get() = countByStatus("Completed") ?: 0
    val inProgress: Int get() = countByStatus("In progress") ?: 0
    val planning: Int get() = countByStatus("Planning") ?: 0
    val paused: Int get() = countByStatus("Paused") ?: 0
    val dropped: Int get() = countByStatus("Dropped") ?: 0

    private fun countByStatus(name: String): Int? {
        val raw = statusDistribution ?: return null
        // Try common shapes — top-level int, or nested {total: int}
        when (val v = raw[name]) {
            is Number -> return v.toInt()
            is Map<*, *> -> (v["total"] as? Number)?.let { return it.toInt() }
            else -> {}
        }
        // Some servers return {Completed: {tv: 12, movie: 3}} — sum sub-counts
        (raw[name] as? Map<*, *>)?.let { sub ->
            return sub.values.filterIsInstance<Number>().sumOf { it.toInt() }
        }
        return null
    }
}

/**
 * Calendar event from CalendarView, serialized by EventSerializer.
 */
data class CalendarEvent(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("item") val item: Item? = null,
    @SerializedName("item_id") val itemId: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    // Event model field is `datetime` (a DateTimeField), serialized as an
    // ISO-8601 string. The old "date" key never existed server-side which
    // is why the calendar chip rendered "?".
    @SerializedName("datetime") val datetime: String? = null,
    @SerializedName("content_number") val contentNumber: Int? = null
) {
    val title: String get() = item?.title.orEmpty()
    val image: String? get() = item?.image
    val mediaType: MediaType? get() = item?.mediaType?.let { MediaType.fromValue(it) }
    val date: String? get() = datetime
    val seasonNumber: Int? get() = item?.seasonNumber
    val episodeNumber: Int? get() = item?.episodeNumber ?: contentNumber
}

/**
 * Custom list. From ListSerializer.
 */
data class CustomList(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("owner") val owner: ListUser? = null,
    @SerializedName("collaborators") val collaborators: List<ListUser>? = null,
    @SerializedName("items_count") val itemsCount: Int = 0,
    @SerializedName("latest_update") val latestUpdate: String? = null,
    @SerializedName("items") val items: PaginatedResponse<MediaItem>? = null
)

data class ListUser(
    @SerializedName("id") val id: Long,
    @SerializedName("username") val username: String
)

/**
 * Standard error envelope. The API consistently returns {"detail": "..."}
 * (sometimes with an "errors" sub-field for validation problems).
 */
data class ApiError(
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("errors") val errors: Any? = null
) {
    fun getErrorMessage(): String = detail ?: "Unknown error"
}

/**
 * Request body for POST /api/v1/media/{media_type}/.
 *
 * The server will:
 *  - default status to "Planning" (numeric 0) if omitted
 *  - convert numeric status -> string via get_media_status(value, reverse=True)
 *  - run the appropriate form for the media_type
 */
data class AddMediaRequest(
    @SerializedName("media_id") val mediaId: String,
    @SerializedName("source") val source: String,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * Request body for PATCH /api/v1/media/{media_type}/{source}/{media_id}/.
 * Only fields in MEDIA_MODIFIABLE_FIELDS for the given media type are honored.
 */
data class UpdateMediaRequest(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("progress") val progress: Int? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    @SerializedName("notes") val notes: String? = null
)

/**
 * Auth result for testConnection() and login flows.
 */
sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Generic result wrapper.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
