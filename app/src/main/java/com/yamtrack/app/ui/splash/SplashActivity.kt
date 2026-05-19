package com.yamtrack.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.app.TaskStackBuilder
import com.yamtrack.app.MainActivity
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.episodes.EpisodesActivity
import com.yamtrack.app.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Dispatcher activity that decides where to send the user without flashing
 * the login UI when a session already exists.
 *
 * The decision uses DataStore reads (synchronous via runBlocking{}.first()
 * — these are tiny disk reads, fine on the launcher hot path), and we
 * never call setContentView so a splash theme remains visible the whole
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

            val mediaType = intent.getStringExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE)
            val source = intent.getStringExtra(MediaDetailsActivity.EXTRA_SOURCE)
            val mediaId = intent.getStringExtra(MediaDetailsActivity.EXTRA_MEDIA_ID)
            val season = intent.getIntExtra(EpisodesActivity.EXTRA_SEASON, -1)

            if (source != null && mediaId != null && season >= 0) {
                // Widget episode tap → open that season's episode list
                // (with the show detail and app beneath it).
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
                // Deep link from the widget: open the item with MainActivity
                // beneath it so Back returns to the app rather than the home
                // screen.
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
}
