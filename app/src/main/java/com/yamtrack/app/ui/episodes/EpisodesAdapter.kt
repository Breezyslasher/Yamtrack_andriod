package com.yamtrack.app.ui.episodes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.databinding.ItemEpisodeBinding
import kotlinx.coroutines.launch

/**
 * Episode list. The release-date/length/description line is fetched per
 * episode on demand; results are cached so RecyclerView rebinds don't
 * re-hit the network.
 */
class EpisodesAdapter(
    private val scope: LifecycleCoroutineScope,
    private val titleFor: (Int, MediaItem) -> String,
    private val loadingText: String,
    private val detailProvider: suspend (Int) -> String?
) : ListAdapter<MediaItem, EpisodesAdapter.VH>(Diff()) {

    private val detailCache = HashMap<Int, String?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEpisodeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    inner class VH(private val binding: ItemEpisodeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ep: MediaItem) {
            val epNum = ep.item?.episodeNumber ?: 0
            binding.tvTitle.text = titleFor(epNum, ep)
            binding.ivThumb.contentDescription = binding.tvTitle.text

            val image = ep.image
            if (!image.isNullOrBlank()) {
                binding.ivThumb.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(6f))
                }
            } else {
                binding.ivThumb.setImageResource(R.drawable.placeholder_poster)
            }

            val cached = detailCache[epNum]
            if (detailCache.containsKey(epNum)) {
                applyDetail(cached)
            } else {
                binding.tvDetail.text = loadingText
                binding.tvDetail.visibility = View.VISIBLE
                scope.launch {
                    val d = runCatching { detailProvider(epNum) }.getOrNull()
                    detailCache[epNum] = d
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        getItem(bindingAdapterPosition).item?.episodeNumber == epNum
                    ) {
                        applyDetail(d)
                    }
                }
            }
        }

        private fun applyDetail(text: String?) {
            if (text.isNullOrBlank()) {
                binding.tvDetail.visibility = View.GONE
            } else {
                binding.tvDetail.text = text
                binding.tvDetail.visibility = View.VISIBLE
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(o: MediaItem, n: MediaItem) =
            o.item?.episodeNumber == n.item?.episodeNumber
        override fun areContentsTheSame(o: MediaItem, n: MediaItem) = o == n
    }
}
