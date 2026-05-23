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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaItem
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
        private const val MENU_ADD_TO_LIST = 1
    }

    private lateinit var binding: ActivityMediaDetailsBinding
    private val viewModel: MediaDetailsViewModel by viewModels()

    private lateinit var mediaType: MediaType
    private lateinit var source: String
    private lateinit var mediaId: String

    private lateinit var recommendationsAdapter: RecommendationsAdapter
    private lateinit var seasonsAdapter: SeasonsAdapter

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

        // item_media_card is match_parent (fills a library grid column); in
        // this horizontal rail that stretches each card to the full screen,
        // so pin a fixed card width like the home rails do.
        val seasonCardWidth = (resources.displayMetrics.density * 120).toInt()
        seasonsAdapter = SeasonsAdapter(seasonCardWidth) { season ->
            openEpisodes(season)
        }
        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(
                this@MediaDetailsActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = seasonsAdapter
        }
    }

    private fun openEpisodes(season: MediaItem) {
        val seasonNumber = season.item?.seasonNumber ?: return
        startActivity(
            android.content.Intent(this, com.yamtrack.app.ui.episodes.EpisodesActivity::class.java).apply {
                putExtra(com.yamtrack.app.ui.episodes.EpisodesActivity.EXTRA_SOURCE, source)
                putExtra(com.yamtrack.app.ui.episodes.EpisodesActivity.EXTRA_MEDIA_ID, mediaId)
                putExtra(com.yamtrack.app.ui.episodes.EpisodesActivity.EXTRA_SEASON, seasonNumber)
            }
        )
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
        // Long-press the poster to bring up media actions (Add to list).
        // Cover/image change isn't an API-supported action, so it's omitted.
        binding.ivPoster.isLongClickable = true
        binding.ivPoster.setOnLongClickListener { anchor ->
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, MENU_ADD_TO_LIST, 0, R.string.add_to_list)
                setOnMenuItemClickListener { item ->
                    if (item.itemId == MENU_ADD_TO_LIST) {
                        showAddToListDialog(); true
                    } else false
                }
                show()
            }
            true
        }
    }

    private fun showAddToListDialog() {
        val current = (viewModel.details.value as? Result.Success)?.data
        val tracked = current?.tracked == true
        if (!tracked) {
            Toast.makeText(this, R.string.add_to_list_requires_track, Toast.LENGTH_LONG).show()
            return
        }
        val containingIds = current?.lists.orEmpty()
            .mapNotNull { it.id }.toHashSet()

        lifecycleScope.launchWhenStarted {
            val all = viewModel.fetchUserLists()
            if (all.isEmpty()) {
                Toast.makeText(
                    this@MediaDetailsActivity,
                    R.string.add_to_list_no_lists,
                    Toast.LENGTH_LONG
                ).show()
                return@launchWhenStarted
            }
            val names = all.map { it.name }.toTypedArray()
            val checked = BooleanArray(all.size) { containingIds.contains(all[it].id) }
            AlertDialog.Builder(this@MediaDetailsActivity)
                .setTitle(R.string.add_to_list)
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton(R.string.save) { _, _ ->
                    all.forEachIndexed { i, list ->
                        val was = containingIds.contains(list.id)
                        val now = checked[i]
                        when {
                            now && !was -> viewModel.addToList(list.id)
                            !now && was -> viewModel.removeFromList(list.id)
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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
                // Server only accepts 0–10; it has no "unset" — so there is
                // no Clear action, an empty/out-of-range entry is rejected.
                if (parsed == null || parsed < 0.0 || parsed > 10.0) {
                    Toast.makeText(this, R.string.set_score_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateScore(parsed)
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
                    Toast.makeText(this, R.string.updated, Toast.LENGTH_SHORT).show()
                }
                is MediaDetailsViewModel.OperationResult.Error -> {
                    Toast.makeText(this, opResult.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.removeResult.observe(this) { opResult ->
            when (opResult) {
                is MediaDetailsViewModel.OperationResult.Success -> {
                    Toast.makeText(this, R.string.removed_from_library, Toast.LENGTH_SHORT).show()
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

        viewModel.seasons.observe(this) { seasons ->
            seasonsAdapter.submitList(seasons)
            val visible = seasons.isNotEmpty()
            binding.tvSeasonsHeader.visibility = if (visible) View.VISIBLE else View.GONE
            binding.rvSeasons.visibility = if (visible) View.VISIBLE else View.GONE
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

            // Release year / air-date range, e.g. "2011" or "2011 - 2013".
            val release = item.releaseLabel
            if (!release.isNullOrBlank()) {
                tvReleaseDate.text = release
                tvReleaseDate.visibility = View.VISIBLE
            } else {
                tvReleaseDate.visibility = View.GONE
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
            .setTitle(R.string.remove_confirm_title)
            .setMessage(R.string.remove_confirm_message)
            .setPositiveButton(R.string.remove) { _, _ -> viewModel.remove() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
