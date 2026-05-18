package com.yamtrack.app.ui.details

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.SearchResult
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

    private lateinit var recommendationsAdapter: RecommendationsAdapter

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
        setupRecommendations()
        observeViewModel()

        viewModel.loadDetails(mediaType, source, mediaId)
    }

    private fun setupRecommendations() {
        recommendationsAdapter = RecommendationsAdapter { rec ->
            openRecommendation(rec)
        }
        binding.rvRecommendations.apply {
            layoutManager = LinearLayoutManager(
                this@MediaDetailsActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = recommendationsAdapter
        }
    }

    private fun openRecommendation(rec: SearchResult) {
        val type = rec.type ?: return
        val recSource = rec.source ?: return
        val recId = rec.mediaId ?: return
        val intent = Intent(this, MediaDetailsActivity::class.java).apply {
            putExtra(EXTRA_MEDIA_TYPE, type.value)
            putExtra(EXTRA_SOURCE, recSource)
            putExtra(EXTRA_MEDIA_ID, recId)
        }
        startActivity(intent)
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
        // The library button's role (add vs remove) is bound in the
        // viewModel.tracked observer once details load.
        binding.tvScore.setOnClickListener {
            showScoreDialog()
        }
    }

    private fun bindLibraryButton(tracked: Boolean) {
        if (tracked) {
            binding.btnRemove.text = getString(R.string.remove_from_library)
            binding.btnRemove.setIconResource(R.drawable.ic_delete)
            binding.btnRemove.setOnClickListener { showRemoveConfirmation() }
        } else {
            binding.btnRemove.text = getString(R.string.add_to_library)
            binding.btnRemove.setIconResource(R.drawable.ic_add_circle)
            binding.btnRemove.setOnClickListener { viewModel.addToLibrary() }
        }
    }

    private fun showScoreDialog() {
        val current = (viewModel.details.value as? Result.Success)?.data?.userScore
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.set_score_hint)
            current?.let { setText(String.format("%.1f", it)) }
        }
        val padding = (resources.displayMetrics.density * 20).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.set_score_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val raw = input.text.toString().trim()
                val parsed = raw.toDoubleOrNull()
                if (parsed == null || parsed < 0.0 || parsed > 10.0) {
                    Toast.makeText(this, R.string.set_score_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateScore(parsed)
            }
            .setNeutralButton(R.string.clear_score) { _, _ ->
                viewModel.updateScore(null)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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

        viewModel.tracked.observe(this) { tracked ->
            bindLibraryButton(tracked)
        }

        viewModel.recommendations.observe(this) { recs ->
            recommendationsAdapter.submitList(recs)
            val visible = recs.isNotEmpty()
            binding.tvRecommendationsHeader.visibility =
                if (visible) View.VISIBLE else View.GONE
            binding.rvRecommendations.visibility =
                if (visible) View.VISIBLE else View.GONE
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

            // Source/public score from the upstream provider — read-only.
            val sourceScore = item.score
            if (sourceScore != null && sourceScore > 0) {
                tvSourceScore.text = getString(R.string.source_score_format, sourceScore)
                tvSourceScore.visibility = View.VISIBLE
            } else {
                tvSourceScore.visibility = View.GONE
            }

            // User's score — always visible & clickable so they can tap to
            // rate even when nothing has been set yet.
            val userScore = item.userScore
            tvScore.text = if (userScore != null && userScore > 0) {
                getString(R.string.your_score_format, userScore)
            } else {
                getString(R.string.tap_to_rate)
            }
            tvScore.visibility = View.VISIBLE

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
