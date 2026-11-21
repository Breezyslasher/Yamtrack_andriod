package com.yamtrack.app.ui.episodes

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import coil.load
import com.yamtrack.app.R
import com.yamtrack.app.data.model.WatchProvider
import com.yamtrack.app.databinding.ItemWatchOptionBinding

/** The services in an episode's "Watch on…" dialog: logo, name and a note. */
class WatchOptionsAdapter(
    private val context: Context,
    private val rows: List<Row>,
) : BaseAdapter() {

    /** [note]: e.g. that the link opens the show, or the app isn't installed. */
    data class Row(val provider: WatchProvider, val note: String?)

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = convertView?.let { ItemWatchOptionBinding.bind(it) }
            ?: ItemWatchOptionBinding.inflate(LayoutInflater.from(context), parent, false)
        val row = rows[position]
        val name = row.provider.providerName.orEmpty()
        binding.tvName.text = name
        binding.tvNote.text = row.note
        binding.tvNote.visibility = if (row.note == null) View.GONE else View.VISIBLE
        binding.root.contentDescription = listOfNotNull(name, row.note).joinToString(", ")
        val image = row.provider.image
        if (!image.isNullOrBlank()) {
            binding.ivLogo.load(image) {
                crossfade(true)
                error(R.drawable.placeholder_poster)
            }
        } else {
            binding.ivLogo.setImageResource(R.drawable.placeholder_poster)
        }
        return binding.root
    }
}
