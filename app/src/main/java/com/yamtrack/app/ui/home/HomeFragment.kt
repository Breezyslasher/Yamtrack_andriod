package com.yamtrack.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UserStats
import com.yamtrack.app.databinding.FragmentHomeBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var recentMediaAdapter: MediaAdapter

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
        setupRecyclerView()
        observeViewModel()
        setupSwipeRefresh()

        viewModel.loadData()
    }

    private fun setupRecyclerView() {
        recentMediaAdapter = MediaAdapter { mediaItem ->
            val type = mediaItem.mediaType ?: return@MediaAdapter
            val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type.value)
                putExtra(MediaDetailsActivity.EXTRA_SOURCE, mediaItem.source)
                putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, mediaItem.mediaId)
            }
            startActivity(intent)
        }

        binding.rvRecentMedia.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentMediaAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.stats.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> updateStats(result.data)
                is Result.Error -> { /* Leave zeros */ }
                is Result.Loading -> { /* No-op */ }
            }
        }

        viewModel.recentMedia.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> {
                    recentMediaAdapter.submitList(result.data)
                    binding.tvEmptyRecent.visibility = 
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Result.Error -> {
                    binding.tvEmptyRecent.visibility = View.VISIBLE
                    binding.tvEmptyRecent.text = result.message
                }
                is Result.Loading -> { /* No-op */ }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }
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
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
