package com.yamtrack.app.ui.details

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

/**
 * Seasons rail adapter. Reuses item_media_card but labels each card by
 * season number ("Season 1") rather than the show title that the generic
 * MediaAdapter would render. Fixed card width so it doesn't stretch in the
 * horizontal rail.
 */
class SeasonsAdapter(
    private val fixedItemWidthPx: Int,
    private val onClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, SeasonsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMediaCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        binding.root.layoutParams = binding.root.layoutParams.apply {
            width = fixedItemWidthPx
        }
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(item: MediaItem) {
            val seasonNumber = item.item?.seasonNumber
            binding.tvTitle.text = if (seasonNumber != null) {
                binding.root.context.getString(R.string.season_number, seasonNumber)
            } else {
                item.title.ifBlank { "Season" }
            }

            binding.tvStatus.visibility = View.GONE
            binding.tvScore.visibility = View.GONE

            val image = item.image
            if (!image.isNullOrBlank()) {
                binding.ivPoster.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(8f))
                }
            } else {
                binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(o: MediaItem, n: MediaItem) =
            o.item?.seasonNumber == n.item?.seasonNumber && o.mediaId == n.mediaId
        override fun areContentsTheSame(o: MediaItem, n: MediaItem) = o == n
    }
}
