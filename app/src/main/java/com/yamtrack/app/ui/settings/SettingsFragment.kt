package com.yamtrack.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.yamtrack.app.BuildConfig
import com.yamtrack.app.R
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UserStats
import com.yamtrack.app.databinding.DialogStatsBinding
import com.yamtrack.app.databinding.FragmentSettingsBinding
import com.yamtrack.app.ui.lists.ListsActivity
import com.yamtrack.app.ui.login.LoginActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Server Settings
        binding.layoutServerUrl.setOnClickListener { showServerUrlDialog() }

        // App Settings
        binding.switchDarkMode.isChecked = true
        binding.switchDarkMode.isEnabled = false

        binding.switchAdultContent.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setShowAdultContent(isChecked)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNotificationsEnabled(isChecked)
        }

        binding.switchHideUnwatched.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHideUnwatchedEpisodeInfo(isChecked)
        }

        // Stats
        binding.layoutStats.setOnClickListener { showStatsDialog() }
        binding.layoutLists.setOnClickListener {
            startActivity(Intent(requireContext(), ListsActivity::class.java))
        }

        // Logout
        binding.btnLogout.setOnClickListener { showLogoutConfirmation() }

        // About
        binding.tvVersion.text = getString(R.string.version_format, BuildConfig.VERSION_NAME)
    }

    private fun observeViewModel() {
        viewModel.serverUrl.observe(viewLifecycleOwner) { url ->
            binding.tvServerUrl.text = url
        }

        viewModel.showAdultContent.observe(viewLifecycleOwner) { show ->
            binding.switchAdultContent.isChecked = show
        }

        viewModel.hideUnwatchedEpisodeInfo.observe(viewLifecycleOwner) { hide ->
            if (binding.switchHideUnwatched.isChecked != hide) {
                binding.switchHideUnwatched.isChecked = hide
            }
        }

        viewModel.notificationsEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.switchNotifications.isChecked = enabled
        }

        viewModel.logoutComplete.observe(viewLifecycleOwner) { complete ->
            if (complete) navigateToLogin()
        }
    }

    private fun showServerUrlDialog() {
        val editText = EditText(requireContext()).apply {
            setText(viewModel.serverUrl.value)
            hint = "https://yamtrack.example.com"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.server_url)
            .setMessage(R.string.server_change_warning)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val newUrl = editText.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    viewModel.setServerUrl(newUrl)
                    Toast.makeText(
                        requireContext(),
                        "Server URL updated. Please login again.",
                        Toast.LENGTH_LONG
                    ).show()
                    viewModel.logout()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default) { _, _ ->
                viewModel.setServerUrl(BuildConfig.DEFAULT_SERVER_URL)
                Toast.makeText(
                    requireContext(),
                    "Server URL reset. Please login again.",
                    Toast.LENGTH_LONG
                ).show()
                viewModel.logout()
            }
            .show()
    }

    private fun showStatsDialog() {
        val dialogBinding = DialogStatsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.stats_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.close, null)
            .create()
        dialog.show()

        val observer = androidx.lifecycle.Observer<Result<UserStats>> { result ->
            when (result) {
                is Result.Loading -> {
                    dialogBinding.progressBar.visibility = View.VISIBLE
                    dialogBinding.tvLoading.visibility = View.VISIBLE
                    dialogBinding.groupContent.visibility = View.GONE
                    dialogBinding.tvError.visibility = View.GONE
                }
                is Result.Success -> {
                    dialogBinding.progressBar.visibility = View.GONE
                    dialogBinding.tvLoading.visibility = View.GONE
                    dialogBinding.tvError.visibility = View.GONE
                    dialogBinding.groupContent.visibility = View.VISIBLE
                    val stats = result.data
                    // /statistics/ has no media_type param, so stats are
                    // shown overall only — no per-type filter.
                    dialogBinding.tvTotal.text = stats.total.toString()
                    dialogBinding.tvCompleted.text = stats.completed.toString()
                    dialogBinding.tvInProgress.text = stats.inProgress.toString()
                    dialogBinding.tvPlanning.text = stats.planning.toString()
                    dialogBinding.tvPaused.text = stats.paused.toString()
                    dialogBinding.tvDropped.text = stats.dropped.toString()
                }
                is Result.Error -> {
                    dialogBinding.progressBar.visibility = View.GONE
                    dialogBinding.tvLoading.visibility = View.GONE
                    dialogBinding.groupContent.visibility = View.GONE
                    dialogBinding.tvError.visibility = View.VISIBLE
                    dialogBinding.tvError.text = result.message
                }
            }
        }
        viewModel.stats.observe(viewLifecycleOwner, observer)
        dialog.setOnDismissListener { viewModel.stats.removeObserver(observer) }

        viewModel.loadStats()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout) { _, _ -> viewModel.logout() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
