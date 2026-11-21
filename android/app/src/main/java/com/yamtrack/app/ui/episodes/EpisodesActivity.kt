package com.yamtrack.app.ui.episodes

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaMeta
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.WatchProvider
import com.yamtrack.app.databinding.ActivityEpisodesBinding
import com.yamtrack.app.links.ProviderLauncher
import com.yamtrack.app.util.fitContentToSystemBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class EpisodesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_SEASON = "season_number"
    }

    private lateinit var binding: ActivityEpisodesBinding
    private val viewModel: EpisodesViewModel by viewModels()
    private lateinit var adapter: EpisodesAdapter
    private var seasonNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitContentToSystemBars()

        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return finish()
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: return finish()
        seasonNumber = intent.getIntExtra(EXTRA_SEASON, -1)
        if (seasonNumber < 0) return finish()

        binding.toolbar.title = getString(R.string.season_number, seasonNumber)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // One-shot read; toggling the pref takes effect on next open.
        val hideUnwatched = kotlinx.coroutines.runBlocking {
            viewModel.hideUnwatchedEpisodeInfo()
        }
        adapter = EpisodesAdapter(
            scope = lifecycleScope,
            titleFor = { epNum, name ->
                getString(
                    R.string.episode_title_format,
                    epNum,
                    name.ifBlank { getString(R.string.episode_fallback_name, epNum) }
                )
            },
            hiddenTitleFor = { epNum -> getString(R.string.episode_hidden_title, epNum) },
            loadingText = getString(R.string.loading),
            hideUnwatchedInfo = hideUnwatched,
            detailProvider = { epNum -> episodeDetailLine(epNum) },
            onWatchedToggled = { epNum, watched ->
                viewModel.setEpisodeWatched(epNum, watched)
            },
            onEpisodeClick = { epNum -> showWatchOptions(epNum) }
        )
        binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        binding.rvEpisodes.adapter = adapter

        viewModel.toast.observe(this) { msg ->
            if (msg != null) {
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                viewModel.toastShown()
            }
        }

        viewModel.episodes.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    adapter.submitList(result.data)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                    if (result.data.isEmpty()) {
                        binding.tvEmpty.text = getString(R.string.episodes_empty)
                    }
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                }
                is Result.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.load(source, mediaId, seasonNumber)
    }

    /**
     * The episode's own name (the details endpoint titles it after the
     * episode), plus release date · runtime and a description below.
     */
    private suspend fun episodeDetailLine(epNum: Int): EpisodesAdapter.EpisodeDetail? {
        val d = viewModel.episodeDetail(epNum) ?: return null
        val header = listOfNotNull(
            MediaMeta.releaseDate(d.details),
            d.runtimeLabel
        ).joinToString(" · ")
        val desc = d.synopsis?.takeIf { it.isNotBlank() }
        val line = listOfNotNull(header.takeIf { it.isNotBlank() }, desc)
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        return EpisodesAdapter.EpisodeDetail(name = d.title, line = line)
    }

    /**
     * "Watch on…" for one episode: its own links, which start that episode in
     * the service's app. Links that only open the show are marked so.
     */
    private fun showWatchOptions(episode: Int) {
        val numbers = adapter.currentList.mapNotNull { it.item?.episodeNumber }
        val index = numbers.indexOf(episode)
        // Compared with the next (else previous) episode to spot show-level links.
        val neighbour = numbers.getOrNull(index + 1) ?: numbers.getOrNull(index - 1)
        lifecycleScope.launch {
            val options = viewModel.watchOptions(episode, neighbour)
            if (options == null) {
                Toast.makeText(this@EpisodesActivity, R.string.episode_links_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val arranged = withContext(Dispatchers.Default) {
                ProviderLauncher.arrangeWithApps(applicationContext, options.providers)
            }
            val providers = arranged.providers
            if (providers.isEmpty()) {
                Toast.makeText(this@EpisodesActivity, R.string.episode_no_links, Toast.LENGTH_SHORT).show()
                return@launch
            }
            fun opensShow(p: WatchProvider) = options.fromShow || (p.tvLink ?: p.link) in options.showLevel

            // Nothing to choose between: one service with its app installed, or
            // only one service at all. Open it straight away.
            val only = arranged.withApp.singleOrNull() ?: providers.singleOrNull()
            if (only != null) {
                if (opensShow(only)) {
                    Toast.makeText(
                        this@EpisodesActivity,
                        getString(R.string.watch_opening_show, only.providerName.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                ProviderLauncher.open(this@EpisodesActivity, only, options.regionLink)
                return@launch
            }

            val rows = providers.map { p ->
                val note = when {
                    opensShow(p) -> getString(R.string.watch_note_opens_show)
                    p !in arranged.withApp -> getString(R.string.watch_note_browser)
                    else -> null
                }
                WatchOptionsAdapter.Row(p, note)
            }
            AlertDialog.Builder(this@EpisodesActivity)
                .setTitle(getString(R.string.watch_episode_on, episode))
                .setAdapter(WatchOptionsAdapter(this@EpisodesActivity, rows)) { _, which ->
                    ProviderLauncher.open(this@EpisodesActivity, providers[which], options.regionLink)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
