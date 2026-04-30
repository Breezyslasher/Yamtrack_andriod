package com.yamtrack.app.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.widget.PopupMenu
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.FragmentLibraryBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import com.yamtrack.app.ui.home.MediaAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var mediaAdapter: MediaAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupMediaTypeChips()
        setupStatusChips()
        setupSortButton()
        observeViewModel()
        setupSwipeRefresh()
    }

    private fun setupSortButton() {
        binding.btnSort.text = labelForSort(viewModel.currentSort())
        binding.btnSort.setOnClickListener { showSortMenu() }
    }

    private fun showSortMenu() {
        val popup = PopupMenu(requireContext(), binding.btnSort)
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val sort = sortValueFor(item.itemId) ?: return@setOnMenuItemClickListener false
            viewModel.setSort(sort)
            binding.btnSort.text = item.title
            true
        }
        popup.show()
    }

    private fun sortValueFor(menuId: Int): String? = when (menuId) {
        R.id.sort_added_desc -> "added_desc"
        R.id.sort_added_asc -> "added"
        R.id.sort_updated_desc -> "updated_desc"
        R.id.sort_title_asc -> "title"
        R.id.sort_title_desc -> "title_desc"
        else -> null
    }

    private fun labelForSort(sort: String): String = when (sort) {
        "added_desc" -> getString(R.string.sort_recently_added)
        "added" -> getString(R.string.sort_oldest_added)
        "updated_desc" -> getString(R.string.sort_recently_updated)
        "title" -> getString(R.string.sort_title_az)
        "title_desc" -> getString(R.string.sort_title_za)
        else -> getString(R.string.sort)
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter { mediaItem ->
            val type = mediaItem.mediaType ?: return@MediaAdapter
            val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type.value)
                putExtra(MediaDetailsActivity.EXTRA_SOURCE, mediaItem.source)
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, mediaItem.mediaId)
            }
            startActivity(intent)
        }

        binding.rvMedia.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = mediaAdapter
        }
    }

    private fun setupMediaTypeChips() {
        binding.chipGroupMediaType.removeAllViews()

        // Only show "parent" types users can filter by; the API treats
        // seasons & episodes as sub-resources of TV (and search rejects them).
        MediaType.parentTypes.forEach { type ->
            val chip = Chip(requireContext()).apply {
                text = type.displayName
                isCheckable = true
                isChecked = type == MediaType.MOVIE
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.setMediaType(type)
                    }
                }
            }
            binding.chipGroupMediaType.addView(chip)
        }
    }

    private fun setupStatusChips() {
        binding.chipGroupStatus.removeAllViews()

        // Synthetic "All" chip — clears the status filter.
        val allChip = Chip(requireContext()).apply {
            text = getString(R.string.all)
            isCheckable = true
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) viewModel.setStatus(null)
            }
        }
        binding.chipGroupStatus.addView(allChip)

        MediaStatus.values().forEach { status ->
            val chip = Chip(requireContext()).apply {
                text = status.displayName
                isCheckable = true
                isChecked = false
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.setStatus(status)
                    }
                }
            }
            binding.chipGroupStatus.addView(chip)
        }
    }

    private fun observeViewModel() {
        viewModel.mediaList.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> {
                    mediaAdapter.submitList(result.data)
                    binding.tvEmpty.visibility = 
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmpty.text = getString(R.string.no_media_found)
                }
                is Result.Error -> {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                }
                is Result.Loading -> { /* No-op */ }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
