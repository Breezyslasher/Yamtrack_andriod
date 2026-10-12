package com.yamtrack.app.ui.details

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CastMember
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaImage
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.SearchResult
import com.yamtrack.app.data.model.Sources
import com.yamtrack.app.data.model.TimeToBeat
import com.yamtrack.app.data.model.WatchProvider
import com.yamtrack.app.data.repository.ArtworkOverrides
import com.yamtrack.app.databinding.ActivityMediaDetailsBinding
import com.yamtrack.app.ui.person.PersonActivity
import com.yamtrack.app.widget.CalendarWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        private const val MENU_CHANGE_ARTWORK = 2
    }

    private lateinit var binding: ActivityMediaDetailsBinding
    private val viewModel: MediaDetailsViewModel by viewModels()

    private lateinit var mediaType: MediaType
    private lateinit var source: String
    private lateinit var mediaId: String

    private lateinit var recommendationsAdapter: RecommendationsAdapter
    private lateinit var seasonsAdapter: SeasonsAdapter
    private lateinit var castAdapter: CastAdapter
    private lateinit var freeProvidersAdapter: ProvidersAdapter
    private lateinit var paidProvidersAdapter: ProvidersAdapter
    private var providersLink: String? = null

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
        seasonsAdapter = SeasonsAdapter(
            seasonCardWidth,
            onClick = { season -> openEpisodes(season) },
            onLongClick = { season ->
                season.item?.seasonNumber?.let { showArtworkPicker(it) }
            }
        )
        binding.rvSeasons.apply {
            layoutManager = LinearLayoutManager(
                this@MediaDetailsActivity,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = seasonsAdapter
        }

        // People pages exist for TMDB only; anime voice actors (AniList)
        // render without a click target.
        val onCastClick: ((CastMember) -> Unit)? =
            if (source == Sources.TMDB) ::openPerson else null
        castAdapter = CastAdapter(onCastClick)
        binding.rvCast.horizontalRail(castAdapter)

        freeProvidersAdapter = ProvidersAdapter { openProvidersLink(it) }
        paidProvidersAdapter = ProvidersAdapter { openProvidersLink(it) }
        binding.rvProvidersFree.horizontalRail(freeProvidersAdapter)
        binding.rvProvidersPaid.horizontalRail(paidProvidersAdapter)
        binding.tvProvidersHeader.setOnClickListener { openProvidersLink(null) }
    }

    private fun RecyclerView.horizontalRail(rvAdapter: RecyclerView.Adapter<*>) {
        layoutManager = LinearLayoutManager(
            this@MediaDetailsActivity,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        adapter = rvAdapter
    }

    private fun openPerson(member: CastMember) {
        val personId = member.personId ?: return
        startActivity(
            Intent(this, PersonActivity::class.java).apply {
                putExtra(PersonActivity.EXTRA_SOURCE, Sources.TMDB)
                putExtra(PersonActivity.EXTRA_PERSON_ID, personId)
                putExtra(PersonActivity.EXTRA_NAME, member.name)
            }
        )
    }

    /** TMDB only gives one region-wide "where to watch" link, not per-service deep links. */
    @Suppress("UNUSED_PARAMETER")
    private fun openProvidersLink(provider: WatchProvider?) {
        val link = providersLink ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure {
                Toast.makeText(this, R.string.could_not_open_browser, Toast.LENGTH_SHORT).show()
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
        // Tap the poster to pick new artwork; long-press for all actions.
        binding.ivPoster.setOnClickListener { showArtworkPicker() }
        binding.ivPoster.isLongClickable = true
        binding.ivPoster.setOnLongClickListener { anchor ->
            android.widget.PopupMenu(this, anchor).apply {
                menu.add(0, MENU_CHANGE_ARTWORK, 0, R.string.change_artwork)
                menu.add(0, MENU_ADD_TO_LIST, 1, R.string.add_to_list)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_CHANGE_ARTWORK -> { showArtworkPicker(); true }
                        MENU_ADD_TO_LIST -> { showAddToListDialog(); true }
                        else -> false
                    }
                }
                show()
            }
            true
        }
    }

    /**
     * Lets the user pick the cover of this item, or of one of its seasons
     * when [seasonNumber] is set. The choice is saved on the server, so it
     * applies for every user and on the web app too.
     */
    private fun showArtworkPicker(seasonNumber: Int? = null) {
        lifecycleScope.launchWhenStarted {
            val choices = when (val r = viewModel.fetchArtworkChoices(seasonNumber)) {
                is Result.Success -> r.data
                is Result.Error -> {
                    toast(r.message)
                    return@launchWhenStarted
                }
                Result.Loading -> return@launchWhenStarted
            }
            val custom = choices.customImage
            if (choices.options.isEmpty() && custom == null) {
                toast(getString(R.string.artwork_none))
                return@launchWhenStarted
            }

            var dialog: AlertDialog? = null
            val pad = (resources.displayMetrics.density * 12).toInt()
            val grid = RecyclerView(this@MediaDetailsActivity).apply {
                layoutManager = GridLayoutManager(this@MediaDetailsActivity, 3)
                setPadding(pad, pad, pad, 0)
                clipToPadding = false
                adapter = ArtworkAdapter(choices.options, custom) onPick@{ picked: MediaImage ->
                    val url = picked.image ?: picked.original ?: return@onPick
                    dialog?.dismiss()
                    saveCover(url, seasonNumber)
                }
            }

            val builder = AlertDialog.Builder(this@MediaDetailsActivity)
                .setTitle(if (seasonNumber != null) R.string.change_season_cover else R.string.change_artwork)
                .setView(grid)
                .setNegativeButton(R.string.cancel, null)
            if (custom != null) {
                builder.setNeutralButton(R.string.reset_artwork) { _, _ ->
                    saveCover(null, seasonNumber)
                }
            }
            dialog = builder.show()
        }
    }

    /** [url] null resets to the provider image. */
    private fun saveCover(url: String?, seasonNumber: Int?) {
        lifecycleScope.launch {
            when (val r = viewModel.applyCover(url, seasonNumber)) {
                is Result.Success -> {
                    // Lists everywhere rebind through ArtworkOverrides.version;
                    // the widget refetches from the server.
                    CalendarWidgetProvider.requestRefresh(this@MediaDetailsActivity)
                    toast(getString(if (url != null) R.string.artwork_updated else R.string.artwork_reset))
                }
                is Result.Error -> toast(getString(R.string.artwork_save_failed, r.message))
                Result.Loading -> Unit
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun bindPoster(item: MediaDetails) {
        val url = item.displayImage
        if (!url.isNullOrBlank()) {
            binding.ivPoster.load(url) {
                crossfade(true)
                placeholder(R.drawable.placeholder_poster)
                error(R.drawable.placeholder_poster)
                transformations(RoundedCornersTransformation(16f))
            }
        } else {
            binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
        }
    }

    private fun bindTimeToBeat(ttb: TimeToBeat?) {
        val show = mediaType == MediaType.GAME && ttb != null && !ttb.isEmpty
        binding.tvTimeToBeatHeader.visibility = if (show) View.VISIBLE else View.GONE
        binding.layoutTimeToBeat.visibility = if (show) View.VISIBLE else View.GONE
        if (!show || ttb == null) return
        binding.tvTtbHastily.text = formatHours(ttb.hastily)
        binding.tvTtbNormally.text = formatHours(ttb.normally)
        binding.tvTtbCompletely.text = formatHours(ttb.completely)
    }

    /** Seconds -> "12h" / "12.5h"; IGDB times are coarse so one decimal is plenty. */
    private fun formatHours(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return getString(R.string.time_unknown)
        val hours = seconds / 3600.0
        val text = if (hours >= 10 || hours % 1.0 < 0.05) {
            Math.round(hours).toString()
        } else {
            String.format(java.util.Locale.getDefault(), "%.1f", hours)
        }
        return getString(R.string.time_hours_format, text)
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

        viewModel.cast.observe(this) { cast ->
            castAdapter.submitList(cast)
            val visible = cast.isNotEmpty()
            binding.tvCastHeader.visibility = if (visible) View.VISIBLE else View.GONE
            binding.rvCast.visibility = if (visible) View.VISIBLE else View.GONE
        }

        viewModel.providers.observe(this) { providers ->
            providersLink = providers?.link
            val free = providers?.noCharge.orEmpty()
            val paid = providers?.paid.orEmpty()
            freeProvidersAdapter.submitList(free)
            paidProvidersAdapter.submitList(paid)
            binding.tvProvidersHeader.visibility =
                if (free.isNotEmpty() || paid.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvProvidersFreeLabel.visibility = if (free.isNotEmpty()) View.VISIBLE else View.GONE
            binding.rvProvidersFree.visibility = if (free.isNotEmpty()) View.VISIBLE else View.GONE
            binding.tvProvidersPaidLabel.visibility = if (paid.isNotEmpty()) View.VISIBLE else View.GONE
            binding.rvProvidersPaid.visibility = if (paid.isNotEmpty()) View.VISIBLE else View.GONE
        }

        // Artwork changed elsewhere (or here) — rebind everything that
        // shows an overridable image.
        ArtworkOverrides.version.observe(this) {
            (viewModel.details.value as? Result.Success)?.data?.let { bindPoster(it) }
            recommendationsAdapter.notifyDataSetChanged()
            seasonsAdapter.notifyDataSetChanged()
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

            bindPoster(item)
            bindTimeToBeat(item.timeToBeat)

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
