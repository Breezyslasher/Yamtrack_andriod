package com.yamtrack.app.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.databinding.ItemCalendarEventBinding
import java.text.SimpleDateFormat
import java.util.Locale

class CalendarAdapter(
    private val onClick: (CalendarEvent) -> Unit
) : ListAdapter<CalendarEvent, CalendarAdapter.VH>(Diff()) {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayFmt = SimpleDateFormat("d", Locale.getDefault())
    private val monthFmt = SimpleDateFormat("MMM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCalendarEventBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val binding: ItemCalendarEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }

        fun bind(event: CalendarEvent) {
            binding.tvTitle.text = event.title.ifBlank { "—" }

            // Date may arrive as a full ISO timestamp; only the date portion
            // matters for the day/month chip.
            val dateStr = event.date?.substring(0, minOf(10, event.date.length))
            val parsed = dateStr?.let {
                runCatching { isoParser.parse(it) }.getOrNull()
            }
            if (parsed != null) {
                binding.tvDay.text = dayFmt.format(parsed)
                binding.tvMonth.text = monthFmt.format(parsed)
            } else {
                binding.tvDay.text = "?"
                binding.tvMonth.text = ""
            }

            binding.tvDetail.text = buildDetailLine(event)

            val image = event.image
            if (!image.isNullOrBlank()) {
                binding.ivPoster.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_poster)
                    error(R.drawable.placeholder_poster)
                    transformations(RoundedCornersTransformation(6f))
                }
            } else {
                binding.ivPoster.setImageResource(R.drawable.placeholder_poster)
            }
        }

        private fun buildDetailLine(event: CalendarEvent): String {
            val typeLabel = event.mediaType?.displayName.orEmpty()
            val parts = mutableListOf<String>()
            if (typeLabel.isNotBlank()) parts.add(typeLabel)
            event.seasonNumber?.let { parts.add("Season $it") }
            event.episodeNumber?.let { parts.add("Episode $it") }
                ?: event.contentNumber?.let { parts.add("# $it") }
            return parts.joinToString(" · ")
        }
    }

    class Diff : DiffUtil.ItemCallback<CalendarEvent>() {
        override fun areItemsTheSame(o: CalendarEvent, n: CalendarEvent): Boolean =
            o.id == n.id && o.date == n.date
        override fun areContentsTheSame(o: CalendarEvent, n: CalendarEvent): Boolean = o == n
    }
}
