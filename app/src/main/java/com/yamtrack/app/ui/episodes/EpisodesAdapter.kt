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
 *
 * The episodes list names an untracked episode after itself but a tracked
 * one after the show (the server stores episodes under the show's title),
 * so the real name is remembered -- from the list while untracked and from
 * the episode's detail call -- and shown instead.
 */
class EpisodesAdapter(
    private val scope: LifecycleCoroutineScope,
    private val titleFor: (episodeNumber: Int, name: String) -> String,
    private val hiddenTitleFor: (Int) -> String,
    private val loadingText: String,
    private val hideUnwatchedInfo: Boolean,
    private val detailProvider: suspend (Int) -> EpisodeDetail?,
    private val onWatchedToggled: (episodeNumber: Int, watched: Boolean) -> Unit
) : ListAdapter<MediaItem, EpisodesAdapter.VH>(Diff()) {

    /** [name] is the episode's own title; [line] the date/length/description. */
    data class EpisodeDetail(val name: String?, val line: String?)

    private val detailCache = HashMap<Int, EpisodeDetail?>()
    private val names = HashMap<Int, String>()

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
            val hide = hideUnwatchedInfo && !ep.tracked
            if (!ep.tracked && ep.title.isNotBlank()) names[epNum] = ep.title

            binding.tvTitle.text =
                if (hide) hiddenTitleFor(epNum) else titleFor(epNum, names[epNum] ?: ep.title)
            binding.ivThumb.contentDescription = binding.tvTitle.text

            binding.cbWatched.setOnCheckedChangeListener(null)
            binding.cbWatched.isChecked = ep.tracked
            binding.cbWatched.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != ep.tracked) onWatchedToggled(epNum, isChecked)
            }

            val image = ep.image
            if (!hide && !image.isNullOrBlank()) {
                binding.ivThumb.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(6f))
                }
            } else {
                binding.ivThumb.setImageResource(R.drawable.placeholder_poster)
            }

            if (hide) {
                // Spoiler shield: don't load or render the rich detail line.
                binding.tvDetail.visibility = View.GONE
                return
            }

            val cached = detailCache[epNum]
            if (detailCache.containsKey(epNum)) {
                applyDetail(cached?.line)
            } else {
                binding.tvDetail.text = loadingText
                binding.tvDetail.visibility = View.VISIBLE
                scope.launch {
                    val d = runCatching { detailProvider(epNum) }.getOrNull()
                    detailCache[epNum] = d
                    d?.name?.takeIf { it.isNotBlank() }?.let { names[epNum] = it }
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION &&
                        getItem(bindingAdapterPosition).item?.episodeNumber == epNum
                    ) {
                        names[epNum]?.let {
                            binding.tvTitle.text = titleFor(epNum, it)
                            binding.ivThumb.contentDescription = binding.tvTitle.text
                        }
                        applyDetail(d?.line)
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
