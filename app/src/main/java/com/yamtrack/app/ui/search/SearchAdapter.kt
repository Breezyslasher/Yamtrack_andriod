package com.yamtrack.app.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.SearchResult
import com.yamtrack.app.databinding.ItemSearchResultBinding

class SearchAdapter(
    private val onItemClick: (SearchResult) -> Unit,
    private val onAddClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchAdapter.SearchViewHolder>(SearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SearchViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.btnAdd.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAddClick(getItem(position))
                }
            }
        }

        fun bind(item: SearchResult) {
            binding.tvTitle.text = item.displayTitle

            // Release date now lives next to the media-type badge, so the
            // standalone year line is redundant.
            binding.tvYear.visibility = View.GONE

            val typeLabel = item.type?.displayName ?: item.mediaType.orEmpty()
            val year = item.year?.takeIf { it.isNotBlank() }
            binding.tvType.text = if (year != null) "$typeLabel · $year" else typeLabel

            if (!item.image.isNullOrBlank()) {
                binding.ivPoster.load(item.image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(8f))
                }
            } else {
                binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
            }

            // Visually distinguish items already being tracked
            if (item.tracked) {
                binding.btnAdd.alpha = 0.4f
            } else {
                binding.btnAdd.alpha = 1.0f
            }
            binding.btnAdd.visibility = View.VISIBLE
        }
    }

    class SearchDiffCallback : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem.mediaId == newItem.mediaId &&
                   oldItem.source == newItem.source &&
                   oldItem.mediaType == newItem.mediaType
        }

        override fun areContentsTheSame(oldItem: SearchResult, newItem: SearchResult): Boolean {
            return oldItem == newItem
        }
    }
}
