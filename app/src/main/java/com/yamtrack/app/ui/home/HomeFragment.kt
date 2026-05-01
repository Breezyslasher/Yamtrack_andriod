package com.yamtrack.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UserStats
import com.yamtrack.app.databinding.FragmentHomeBinding
import com.yamtrack.app.databinding.SectionMediaGroupBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        setupSwipeRefresh()

        viewModel.loadData()
    }

    private fun observeViewModel() {
        viewModel.stats.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> updateStats(result.data)
                is Result.Error -> { /* Leave zeros */ }
                is Result.Loading -> { /* No-op */ }
            }
        }

        viewModel.recentByType.observe(viewLifecycleOwner) { groups ->
            renderGroups(binding.llRecentSections, groups)
            binding.tvEmptyRecent.visibility =
                if (groups.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.planningByType.observe(viewLifecycleOwner) { groups ->
            renderGroups(binding.llPlanningSections, groups)
            binding.tvPlanningHeader.visibility =
                if (groups.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
    }

    /**
     * Build (or rebuild) one horizontal poster row per media-type group.
     * Recreated wholesale on every emission for simplicity — the lists are
     * small and this is invoked only after data loads or swipe-refresh.
     */
    private fun renderGroups(
        container: LinearLayout,
        groups: Map<MediaType, List<MediaItem>>
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        // Item layout is match_parent so it fills its column in the library
        // grid. Pin a pixel width here so cards don't stretch to the full
        // viewport in horizontal rails.
        val cardWidthPx = (resources.displayMetrics.density * 128).toInt()
        groups.forEach { (type, items) ->
            val sectionBinding = SectionMediaGroupBinding.inflate(inflater, container, false)
            sectionBinding.tvSectionTitle.text = type.displayName

            val adapter = MediaAdapter(fixedItemWidthPx = cardWidthPx) { item ->
                openDetails(item)
            }
            sectionBinding.rvSection.apply {
                layoutManager = LinearLayoutManager(
                    context, LinearLayoutManager.HORIZONTAL, false
                )
                this.adapter = adapter
                isNestedScrollingEnabled = false
                setHasFixedSize(true)
            }
            adapter.submitList(items)

            container.addView(sectionBinding.root)
        }
    }

    private fun openDetails(item: MediaItem) {
        val type = item.mediaType ?: return
        val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type.value)
            putExtra(MediaDetailsActivity.EXTRA_SOURCE, item.source)
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, item.mediaId)
        }
        startActivity(intent)
    }

    private fun updateStats(stats: UserStats) {
        binding.apply {
            tvStatTotal.text = stats.total.toString()
            tvStatCompleted.text = stats.completed.toString()
            tvStatWatching.text = stats.inProgress.toString()
            tvStatPlanning.text = stats.planning.toString()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadData() }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
