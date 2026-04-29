package com.yamtrack.app.ui.details

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.ActivityMediaDetailsBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Detail screen for a tracked item.
 * 
 * The new API requires three path params: media_type, source (tmdb/mal/igdb/etc.),
 * and media_id. All three are passed as intent extras.
 */
@AndroidEntryPoint
class MediaDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_MEDIA_ID = "media_id"
    }

    private lateinit var binding: ActivityMediaDetailsBinding
    private val viewModel: MediaDetailsViewModel by viewModels()

    private lateinit var mediaType: MediaType
    private lateinit var source: String
    private lateinit var mediaId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val typeStr = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: "movie"
        mediaType = MediaType.fromValue(typeStr) ?: MediaType.MOVIE
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "tmdb"
        mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: return finish()

        setupToolbar()
        setupStatusChips()
        setupButtons()
        observeViewModel()

        viewModel.loadDetails(mediaType, source, mediaId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupStatusChips() {
        binding.chipGroupStatus.removeAllViews()

        // Show all 5 trackable statuses. The "ALL" filter from the library
        // screen doesn't apply to a single item.
        MediaStatus.values().forEach { status ->
            val chip = Chip(this).apply {
                text = status.displayName
                isCheckable = true
                tag = status
                setOnClickListener {
                    if ((it as Chip).isChecked) {
                        viewModel.updateStatus(status)
                    }
                }
            }
            binding.chipGroupStatus.addView(chip)
        }
    }

    private fun setupButtons() {
        binding.btnRemove.setOnClickListener {
            showRemoveConfirmation()
        }
    }

    private fun observeViewModel() {
        viewModel.details.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    displayDetails(result.data)
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                is Result.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.contentLayout.visibility = View.GONE
                }
            }
        }

        viewModel.updateResult.observe(this) { opResult ->
            when (opResult) {
                is MediaDetailsViewModel.OperationResult.Success -> {
                    Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
                }
                is MediaDetailsViewModel.OperationResult.Error -> {
                    Toast.makeText(this, opResult.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.removeResult.observe(this) { opResult ->
            when (opResult) {
                is MediaDetailsViewModel.OperationResult.Success -> {
                    Toast.makeText(this, "Removed from library", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is MediaDetailsViewModel.OperationResult.Error -> {
                    Toast.makeText(this, opResult.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayDetails(item: MediaDetails) {
        binding.apply {
            val displayTitle = item.title.orEmpty()
            toolbar.title = displayTitle
            tvTitle.text = displayTitle

            // Prefer the upstream synopsis; fall back to the user's note if any.
            tvOverview.text = item.synopsis?.takeIf { it.isNotBlank() }
                ?: item.userNotes?.takeIf { it.isNotBlank() }
                ?: "No overview available."

            if (!item.image.isNullOrBlank()) {
                ivPoster.load(item.image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(16f))
                }
            }

            // Status chips reflect the user's current tracked status (or none).
            val currentStatus = item.userStatus
            for (i in 0 until chipGroupStatus.childCount) {
                val chip = chipGroupStatus.getChildAt(i) as? Chip ?: continue
                val chipStatus = chip.tag as? MediaStatus
                chip.isChecked = chipStatus == currentStatus
            }

            // Score: prefer the user's score, fall back to the public score.
            val score = item.userScore ?: item.score
            if (score != null && score > 0) {
                tvScore.text = String.format("%.1f / 10", score)
                tvScore.visibility = View.VISIBLE
            } else {
                tvScore.visibility = View.GONE
            }

            // Genres come from upstream metadata.
            val genres = item.genres.orEmpty()
            if (genres.isNotEmpty()) {
                chipGroupGenres.removeAllViews()
                genres.forEach { genreName ->
                    val chip = Chip(this@MediaDetailsActivity).apply {
                        text = genreName
                        isClickable = false
                    }
                    chipGroupGenres.addView(chip)
                }
                chipGroupGenres.visibility = View.VISIBLE
            } else {
                chipGroupGenres.visibility = View.GONE
            }
        }
    }

    private fun showRemoveConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Remove from Library")
            .setMessage("Are you sure you want to remove this from your library?")
            .setPositiveButton("Remove") { _, _ -> viewModel.remove() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
