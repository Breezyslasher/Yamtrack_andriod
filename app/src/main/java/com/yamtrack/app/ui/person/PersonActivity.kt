package com.yamtrack.app.ui.person

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.KnownFor
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Person
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.ArtworkOverrides
import com.yamtrack.app.databinding.ActivityPersonBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import dagger.hilt.android.AndroidEntryPoint

/** Person page: photo, bio, and a grid of what they're known for. */
@AndroidEntryPoint
class PersonActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_PERSON_ID = "person_id"
        const val EXTRA_NAME = "name"
    }

    private lateinit var binding: ActivityPersonBinding
    private val viewModel: PersonViewModel by viewModels()
    private lateinit var knownForAdapter: KnownForAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val source = intent.getStringExtra(EXTRA_SOURCE) ?: return finish()
        val personId = intent.getLongExtra(EXTRA_PERSON_ID, -1L)
        if (personId < 0) return finish()

        binding.toolbar.title = intent.getStringExtra(EXTRA_NAME).orEmpty()
        binding.toolbar.setNavigationOnClickListener { finish() }

        knownForAdapter = KnownForAdapter { openMedia(it) }
        binding.rvKnownFor.layoutManager = GridLayoutManager(this, 3)
        binding.rvKnownFor.adapter = knownForAdapter

        setupFilterChips()

        binding.tvBiography.setOnClickListener {
            val expanded = binding.tvBiography.maxLines == Int.MAX_VALUE
            binding.tvBiography.maxLines = if (expanded) 6 else Int.MAX_VALUE
        }

        viewModel.person.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.contentLayout.visibility = View.GONE
                }
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    bindPerson(result.data)
                }
                is Result.Error -> {
                    // Still show "known for" even if the bio call failed.
                    binding.progressBar.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                    binding.tvName.text = binding.toolbar.title
                }
            }
        }

        viewModel.knownFor.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    binding.knownForProgress.visibility = View.VISIBLE
                    binding.tvKnownForEmpty.visibility = View.GONE
                }
                is Result.Success -> {
                    binding.knownForProgress.visibility = View.GONE
                    knownForAdapter.submitList(result.data)
                    binding.tvKnownForEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Result.Error -> {
                    binding.knownForProgress.visibility = View.GONE
                    knownForAdapter.submitList(emptyList())
                    binding.tvKnownForEmpty.text = result.message
                    binding.tvKnownForEmpty.visibility = View.VISIBLE
                }
            }
        }

        ArtworkOverrides.version.observe(this) { knownForAdapter.notifyDataSetChanged() }

        viewModel.load(source, personId)
    }

    private fun setupFilterChips() {
        val options = listOf<Pair<String, MediaType?>>(
            getString(R.string.all) to null,
            getString(R.string.movies) to MediaType.MOVIE,
            getString(R.string.tv_shows) to MediaType.TV
        )
        options.forEachIndexed { index, (label, type) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = index == 0
                setOnClickListener { viewModel.loadKnownFor(type) }
            }
            binding.chipGroupKnownFor.addView(chip)
        }
    }

    private fun bindPerson(person: Person) {
        val name = person.name.orEmpty()
        binding.toolbar.title = name
        binding.tvName.text = name

        if (!person.image.isNullOrBlank()) {
            binding.ivPhoto.load(person.image) {
                crossfade(true)
                error(R.drawable.ic_person)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_person)
        }

        val department = person.knownForDepartment?.takeIf { it.isNotBlank() }
        binding.tvDepartment.text = department
        binding.tvDepartment.visibility = if (department != null) View.VISIBLE else View.GONE

        val facts = buildList {
            person.birthday?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.born_format, it))
            }
            person.placeOfBirth?.takeIf { it.isNotBlank() }?.let { add(it) }
            person.deathday?.takeIf { it.isNotBlank() }?.let {
                add(getString(R.string.died_format, it))
            }
        }
        binding.tvFacts.text = facts.joinToString("\n")
        binding.tvFacts.visibility = if (facts.isNotEmpty()) View.VISIBLE else View.GONE

        val bio = person.biography?.takeIf { it.isNotBlank() }
        binding.tvBiography.text = bio
        binding.tvBiography.visibility = if (bio != null) View.VISIBLE else View.GONE
        binding.tvBiographyHeader.visibility = binding.tvBiography.visibility
    }

    private fun openMedia(item: KnownFor) {
        val type = item.mediaType ?: return
        val src = item.source ?: return
        val id = item.mediaId ?: return
        startActivity(
            Intent(this, MediaDetailsActivity::class.java).apply {
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type)
                putExtra(MediaDetailsActivity.EXTRA_SOURCE, src)
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, id)
            }
        )
    }
}
