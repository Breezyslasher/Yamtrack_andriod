package com.yamtrack.app.ui.search

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.FragmentSearchBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearchInput()
        setupMediaTypeChips()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(
            onItemClick = { result ->
                val type = result.type ?: return@SearchAdapter
                val source = result.source ?: return@SearchAdapter
                val mediaId = result.mediaId ?: return@SearchAdapter
                val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, type.value)
                    putExtra(MediaDetailsActivity.EXTRA_SOURCE, source)
                    putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, mediaId)
                }
                startActivity(intent)
            },
            onAddClick = { result ->
                viewModel.addToLibrary(result)
            }
        )

        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        binding.btnSearch.setOnClickListener { performSearch() }

        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
            searchAdapter.submitList(emptyList())
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun performSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isNotEmpty()) {
            viewModel.search(query)
        }
    }

    private fun setupMediaTypeChips() {
        binding.chipGroupMediaType.removeAllViews()

        // Yamtrack rejects search for season/episode, so we only offer
        // top-level media types here.
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

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> {
                    searchAdapter.submitList(result.data)
                    binding.tvEmpty.visibility = 
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmpty.text = if (result.data.isEmpty()) {
                        "No results found"
                    } else ""
                    binding.progressBar.visibility = View.GONE
                }
                is Result.Error -> {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                    binding.progressBar.visibility = View.GONE
                }
                is Result.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }

        viewModel.addResult.observe(viewLifecycleOwner) { addResult ->
            val msg = when (addResult) {
                is SearchViewModel.AddResult.Success -> "Added \"${addResult.title}\" to library"
                is SearchViewModel.AddResult.Error -> "Failed to add: ${addResult.message}"
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
