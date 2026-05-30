package com.yamtrack.app.ui.splash

import android.app.TaskStackBuilder
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yamtrack.app.MainActivity
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.episodes.EpisodesActivity
import com.yamtrack.app.ui.lists.ListItemsActivity
import com.yamtrack.app.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Dispatcher activity that:
 *  1. Decides between LoginActivity and MainActivity at launch without
 *     flashing the login UI for already-signed-in users.
 *  2. Resolves deep links (yamtrack:// scheme + the default https host)
 *     into the right back-stack:
 *       yamtrack://media/<type>/<source>/<id>             -> media detail
 *       yamtrack://media/tv/<source>/<id>/<season>        -> season episodes
 *       yamtrack://lists/<id>                              -> list items
 *  3. Forwards widget per-row intents that carry EXTRA_* keys directly.
 *
 * Never calls setContentView; the Splash theme stays visible the entire
 * time the activity is alive.
 */
@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var repository: YamtrackRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (loggedIn, token, serverUrl) = runBlocking {
            Triple(
                preferencesManager.isLoggedIn.first(),
                preferencesManager.apiToken.first(),
                preferencesManager.serverUrl.first()
            )
        }

        if (loggedIn && !token.isNullOrBlank()) {
            // Prime the repository so MainActivity's first request succeeds
            // without waiting on a separate session-restore round trip.
            repository.setServerUrl(serverUrl)
            repository.setToken(token)

            val link = intent.data?.let { parseDeepLink(it) }

            // List deep link goes straight to ListItemsActivity beneath the
            // ListsActivity tab (Back returns to the lists screen).
            if (link is DeepLink.ListItems) {
                val items = Intent(this, ListItemsActivity::class.java).apply {
                    putExtra(ListItemsActivity.EXTRA_LIST_ID, link.listId)
                }
                TaskStackBuilder.create(this)
                    .addNextIntent(Intent(this, MainActivity::class.java))
                    .addNextIntent(items)
                    .startActivities()
                finish()
                overridePendingTransition(0, 0)
                return
            }

            val media = link as? DeepLink.Media
            val mediaType = media?.mediaType
                ?: intent.getStringExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE)
            val source = media?.source
                ?: intent.getStringExtra(MediaDetailsActivity.EXTRA_SOURCE)
            val mediaId = media?.mediaId
                ?: intent.getStringExtra(MediaDetailsActivity.EXTRA_MEDIA_ID)
            val season = media?.season
                ?: intent.getIntExtra(EpisodesActivity.EXTRA_SEASON, -1)
                    .takeIf { it >= 0 }

            if (source != null && mediaId != null && season != null) {
                // Episode deep link: app -> show detail -> episodes list.
                val show = Intent(this, MediaDetailsActivity::class.java).apply {
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, MediaType.TV.value)
                    putExtra(MediaDetailsActivity.EXTRA_SOURCE, source)
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, mediaId)
                }
                val episodes = Intent(this, EpisodesActivity::class.java).apply {
                    putExtra(EpisodesActivity.EXTRA_SOURCE, source)
                    putExtra(EpisodesActivity.EXTRA_MEDIA_ID, mediaId)
                    putExtra(EpisodesActivity.EXTRA_SEASON, season)
                }
                TaskStackBuilder.create(this)
                    .addNextIntent(Intent(this, MainActivity::class.java))
                    .addNextIntent(show)
                    .addNextIntent(episodes)
                    .startActivities()
            } else if (mediaType != null && source != null && mediaId != null) {
                val detail = Intent(this, MediaDetailsActivity::class.java).apply {
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, mediaType)
                    putExtra(MediaDetailsActivity.EXTRA_SOURCE, source)
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, mediaId)
                }
                TaskStackBuilder.create(this)
                    .addNextIntent(Intent(this, MainActivity::class.java))
                    .addNextIntent(detail)
                    .startActivities()
            } else {
                startActivity(Intent(this, MainActivity::class.java))
            }
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private sealed class DeepLink {
        data class Media(
            val mediaType: String,
            val source: String,
            val mediaId: String,
            val season: Int?
        ) : DeepLink()
        data class ListItems(val listId: Long) : DeepLink()
    }

    /**
     * Accepts both yamtrack://media/... and https://<configured-host>/media/...
     * Path forms:
     *   /media/<type>/<source>/<id>                    -> Media (no season)
     *   /media/<type>/<source>/<id>/<season>           -> Media (season)
     *   /media/<type>/<source>/<id>/<season>/<episode> -> Media (season)
     *   /lists/<id>                                    -> ListItems
     * Anything that doesn't match returns null and we fall back to the
     * regular launcher behavior.
     */
    private fun parseDeepLink(uri: Uri): DeepLink? {
        val segments = uri.pathSegments
            ?.filter { it.isNotEmpty() }
            ?: return null
        return when (segments.firstOrNull()) {
            "media" -> {
                // [media, type, source, id, season?, episode?]
                if (segments.size < 4) return null
                val season = segments.getOrNull(4)?.toIntOrNull()
                DeepLink.Media(
                    mediaType = segments[1],
                    source = segments[2],
                    mediaId = segments[3],
                    season = season
                )
            }
            "lists" -> segments.getOrNull(1)?.toLongOrNull()?.let { DeepLink.ListItems(it) }
            else -> null
        }
    }
}
