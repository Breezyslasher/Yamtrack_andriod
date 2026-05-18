package com.yamtrack.app.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.databinding.ItemCalendarEventBinding

/** A row is either a date header ("Today", "May 20") or a single event. */
sealed class CalendarRow {
    data class Header(val label: String) : CalendarRow()
    data class Event(val event: CalendarEvent) : CalendarRow()
}

class CalendarAdapter(
    private val onClick: (CalendarEvent) -> Unit
) : ListAdapter<CalendarRow, RecyclerView.ViewHolder>(Diff()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CalendarRow.Header -> TYPE_HEADER
        is CalendarRow.Event -> TYPE_EVENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(
                inflater.inflate(R.layout.item_calendar_header, parent, false) as TextView
            )
        } else {
            EventVH(ItemCalendarEventBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CalendarRow.Header -> (holder as HeaderVH).bind(row)
            is CalendarRow.Event -> (holder as EventVH).bind(row.event)
        }
    }

    inner class HeaderVH(private val text: TextView) : RecyclerView.ViewHolder(text) {
        fun bind(row: CalendarRow.Header) {
            text.text = row.label
        }
    }

    inner class EventVH(private val binding: ItemCalendarEventBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    (getItem(pos) as? CalendarRow.Event)?.let { onClick(it.event) }
                }
            }
        }

        fun bind(event: CalendarEvent) {
            // The date is shown once per group via CalendarRow.Header, so the
            // individual rows no longer carry a day/month chip.
            binding.tvTitle.text = event.title.ifBlank { "—" }
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
            val parts = mutableListOf<String>()
            event.mediaType?.displayName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            event.seasonNumber?.let { parts.add("Season $it") }
            event.episodeNumber?.let { parts.add("Episode $it") }
            return parts.joinToString(" · ")
        }
    }

    class Diff : DiffUtil.ItemCallback<CalendarRow>() {
        override fun areItemsTheSame(o: CalendarRow, n: CalendarRow): Boolean = when {
            o is CalendarRow.Header && n is CalendarRow.Header -> o.label == n.label
            o is CalendarRow.Event && n is CalendarRow.Event ->
                o.event.id == n.event.id && o.event.date == n.event.date
            else -> false
        }

        override fun areContentsTheSame(o: CalendarRow, n: CalendarRow): Boolean = o == n
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EVENT = 1
    }
}
