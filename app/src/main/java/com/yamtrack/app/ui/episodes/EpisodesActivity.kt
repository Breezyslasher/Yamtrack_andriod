package com.yamtrack.app.ui.episodes

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaMeta
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.ActivityEpisodesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EpisodesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_SEASON = "season_number"
    }

    private lateinit var binding: ActivityEpisodesBinding
    private val viewModel: EpisodesViewModel by viewModels()
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

        viewModel.episodes.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    renderEpisodes(result.data)
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                }
                is Result.Loading -> {
                    if (binding.episodeContainer.childCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewModel.load(source, mediaId, seasonNumber)
    }

    private fun renderEpisodes(episodes: List<MediaItem>) {
        binding.episodeContainer.removeAllViews()
        if (episodes.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvEmpty.visibility = View.GONE
        episodes.forEach { ep ->
            val epNum = ep.item?.episodeNumber ?: return@forEach
            binding.episodeContainer.addView(buildEpisodeRow(epNum, ep))
        }
    }

    private fun buildEpisodeRow(epNum: Int, ep: MediaItem): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (density * 10).toInt(), 0, (density * 10).toInt())
        }
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                (density * 120).toInt(), (density * 68).toInt()
            ).apply { marginEnd = (density * 12).toInt() }
            scaleType = ImageView.ScaleType.CENTER_CROP
            ep.image?.takeIf { it.isNotBlank() }?.let { url ->
                load(url) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(6f))
                }
            } ?: setImageResource(R.drawable.placeholder_poster)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val title = TextView(this).apply {
            text = "E$epNum · ${ep.title.ifBlank { "Episode $epNum" }}"
            setTextColor(getColor(R.color.white))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val detail = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            text = getString(R.string.loading)
            setPadding(0, (density * 2).toInt(), 0, 0)
        }
        col.addView(title)
        col.addView(detail)
        row.addView(thumb)
        row.addView(col)

        lifecycleScope.launch {
            val d = viewModel.episodeDetail(epNum)
            if (d == null) {
                detail.visibility = View.GONE
                return@launch
            }
            val bits = mutableListOf<String>()
            MediaMeta.releaseDate(d.details)?.let { bits.add(it) }
            d.runtimeLabel?.let { bits.add(it) }
            val header = bits.joinToString(" · ")
            val desc = d.synopsis?.takeIf { it.isNotBlank() }
            detail.text = listOfNotNull(header.takeIf { it.isNotBlank() }, desc)
                .joinToString("\n")
            if (detail.text.isBlank()) detail.visibility = View.GONE
        }
        return row
    }
}
