package com.yamtrack.app.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yamtrack.app.MainActivity
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
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
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
