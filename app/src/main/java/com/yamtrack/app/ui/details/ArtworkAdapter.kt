package com.yamtrack.app.ui.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.yamtrack.app.R
import com.yamtrack.app.data.model.MediaImage
import com.yamtrack.app.databinding.ItemArtworkBinding

/** Grid of artwork options; the currently applied one is outlined. */
class ArtworkAdapter(
    private val images: List<MediaImage>,
    private val selectedUrl: String?,
    private val onPick: (MediaImage) -> Unit
) : RecyclerView.Adapter<ArtworkAdapter.VH>() {

    override fun getItemCount() = images.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemArtworkBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(images[position])

    inner class VH(private val binding: ItemArtworkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPick(images[pos])
            }
        }

        fun bind(image: MediaImage) {
            val selected = selectedUrl != null &&
                (selectedUrl == image.image || selectedUrl == image.original)
            val density = binding.root.resources.displayMetrics.density
            binding.card.strokeWidth = if (selected) (3 * density).toInt() else 0
            binding.ivArtwork.load(image.image ?: image.original) {
                crossfade(true)
                placeholder(R.drawable.placeholder_poster)
                error(R.drawable.placeholder_poster)
            }
        }
    }
}
