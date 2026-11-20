package com.yamtrack.app.ui.person

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yamtrack.app.R
import com.yamtrack.app.data.model.KnownFor
import com.yamtrack.app.databinding.ItemKnownForBinding

class KnownForAdapter(
    private val onClick: (KnownFor) -> Unit
) : ListAdapter<KnownFor, KnownForAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemKnownForBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemKnownForBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(item: KnownFor) {
            val year = item.releaseDate?.take(4)
            binding.tvTitle.text = if (year.isNullOrBlank()) {
                item.title.orEmpty()
            } else {
                "${item.title.orEmpty()} ($year)"
            }

            // Character for acting credits, job(s) for crew credits.
            val role = item.character?.takeIf { it.isNotBlank() }
                ?: item.jobs.orEmpty().joinToString(", ").takeIf { it.isNotBlank() }
            binding.tvRole.text = role
            binding.tvRole.visibility = if (role != null) View.VISIBLE else View.GONE

            val image = item.displayImage
            if (!image.isNullOrBlank()) {
                binding.ivPoster.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                }
            } else {
                binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<KnownFor>() {
        override fun areItemsTheSame(o: KnownFor, n: KnownFor) =
            o.mediaType == n.mediaType && o.mediaId == n.mediaId && o.source == n.source
        override fun areContentsTheSame(o: KnownFor, n: KnownFor) = o == n
    }
}
