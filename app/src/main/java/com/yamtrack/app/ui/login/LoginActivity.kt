package com.yamtrack.app.ui.login

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.MainActivity
import com.yamtrack.app.R
import com.yamtrack.app.databinding.ActivityLoginBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Login screen for the Yamtrack REST API.
 *
 * The Yamtrack API only supports Bearer / X-API-Key auth — there is no
 * username/password endpoint. Users must paste an API token copied from
 * the Yamtrack web UI profile page.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()

        viewModel.checkExistingSession()
    }

    private fun setupUI() {
        binding.tvServerSettings.setOnClickListener { showServerSettingsDialog() }

        binding.btnLogin.setOnClickListener {
            val url = viewModel.serverUrl.value ?: BuildConfig.DEFAULT_SERVER_URL
            val token = binding.etToken.text.toString().trim()
            viewModel.loginWithToken(url, token)
        }

        binding.tvHowToGetToken.setOnClickListener { showTokenHelpDialog() }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is LoginState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    navigateToMain()
                }
                is LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is LoginState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                }
            }
        }

        viewModel.serverUrl.observe(this) { url ->
            binding.tvCurrentServer.text = getString(R.string.current_server, url)
        }
    }

    private fun showServerSettingsDialog() {
        val currentUrl = viewModel.serverUrl.value ?: BuildConfig.DEFAULT_SERVER_URL
        val dialogView = layoutInflater.inflate(R.layout.dialog_server_settings, null)
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.etServerUrl)
        editText.setText(currentUrl)

        AlertDialog.Builder(this)
            .setTitle(R.string.server_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    viewModel.setServerUrl(newUrl)
                    Toast.makeText(this, "Server URL updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("Reset to Default") { _, _ ->
                viewModel.setServerUrl(BuildConfig.DEFAULT_SERVER_URL)
                Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showTokenHelpDialog() {
        val url = viewModel.serverUrl.value ?: BuildConfig.DEFAULT_SERVER_URL
        val profileUrl = "$url/profile/"

        AlertDialog.Builder(this)
            .setTitle("How to get your API token")
            .setMessage(
                "1. Open your Yamtrack server in a web browser\n" +
                "2. Log in with your username and password\n" +
                "3. Navigate to your profile page\n" +
                "4. Copy your API token\n" +
                "5. Paste it here\n\n" +
                "Your server must be running a Yamtrack build that includes " +
                "PR #924 (the REST API). Check the /api/v1/health/ endpoint to verify.\n\n" +
                "Profile URL: $profileUrl"
            )
            .setPositiveButton("Open Profile") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
