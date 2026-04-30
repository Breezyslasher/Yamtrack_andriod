package com.yamtrack.app.ui.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.SearchResult
import com.yamtrack.app.databinding.ItemRecommendationBinding

class RecommendationsAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, RecommendationsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRecommendationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemRecommendationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(item: SearchResult) {
            binding.tvTitle.text = item.displayTitle
            if (!item.image.isNullOrBlank()) {
                binding.ivPoster.load(item.image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(10f))
                }
            } else {
                binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(o: SearchResult, n: SearchResult): Boolean =
            o.mediaId == n.mediaId && o.source == n.source && o.mediaType == n.mediaType
        override fun areContentsTheSame(o: SearchResult, n: SearchResult): Boolean = o == n
    }
}
