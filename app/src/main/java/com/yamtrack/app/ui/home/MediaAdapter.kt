package com.yamtrack.app.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.databinding.ItemMediaCardBinding

class MediaAdapter(
    /**
     * Optional fixed item width in pixels. Used in horizontal rails (home
     * page sections) where the card layout's `match_parent` would otherwise
     * stretch to the RecyclerView's full width. Library grid passes null
     * so the card fills its grid span.
     */
    private val fixedItemWidthPx: Int? = null,
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    constructor(onItemClick: (MediaItem) -> Unit) : this(null, onItemClick)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        if (fixedItemWidthPx != null) {
            binding.root.layoutParams = binding.root.layoutParams.apply {
                width = fixedItemWidthPx
            }
        }
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(
        private val binding: ItemMediaCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: MediaItem) {
            binding.tvTitle.text = item.title

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

            // Status badge
            val status = item.mediaStatus
            if (status != null) {
                binding.tvStatus.text = status.displayName
                binding.tvStatus.visibility = View.VISIBLE
            } else {
                binding.tvStatus.visibility = View.GONE
            }

            // Score
            val score = item.score
            if (score != null && score > 0) {
                binding.tvScore.text = String.format("%.1f", score)
                binding.tvScore.visibility = View.VISIBLE
            } else {
                binding.tvScore.visibility = View.GONE
            }
        }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.mediaId == newItem.mediaId &&
                   oldItem.source == newItem.source &&
                   oldItem.mediaType == newItem.mediaType
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}
