package com.yamtrack.app.ui.episodes

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaMeta
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.ActivityEpisodesBinding
import dagger.hilt.android.AndroidEntryPoint

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
            titleFor = { epNum, ep ->
                getString(
                    R.string.episode_title_format,
                    epNum,
                    ep.title.ifBlank { getString(R.string.episode_fallback_name, epNum) }
                )
            },
            hiddenTitleFor = { epNum -> getString(R.string.episode_hidden_title, epNum) },
            loadingText = getString(R.string.loading),
            hideUnwatchedInfo = hideUnwatched,
            detailProvider = { epNum -> episodeDetailLine(epNum) },
            onWatchedToggled = { epNum, watched ->
                viewModel.setEpisodeWatched(epNum, watched)
            }
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

    /** Release date · runtime, then a description on the next line. */
    private suspend fun episodeDetailLine(epNum: Int): String? {
        val d = viewModel.episodeDetail(epNum) ?: return null
        val header = listOfNotNull(
            MediaMeta.releaseDate(d.details),
            d.runtimeLabel
        ).joinToString(" · ")
        val desc = d.synopsis?.takeIf { it.isNotBlank() }
        return listOfNotNull(header.takeIf { it.isNotBlank() }, desc)
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
    }
}
