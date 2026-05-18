package com.yamtrack.app.ui.calendar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.yamtrack.app.R
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.databinding.FragmentCalendarBinding
import com.yamtrack.app.ui.details.MediaDetailsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: CalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CalendarAdapter { event -> openEvent(event) }
        binding.rvCalendar.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCalendar.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        binding.tvSubtitle.text = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            .format(Date())

        observe()
    }

    private fun observe() {
        viewModel.events.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Success -> {
                    val rows = buildRows(result.data)
                    adapter.submitList(rows)
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = result.message
                }
                is Result.Loading -> {
                    if (adapter.itemCount == 0) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }
    }

    /**
     * Flatten the sorted events into header + event rows. Days get a
     * relative label (Yesterday/Today/Tomorrow) within ±1 day, otherwise a
     * "Tue, May 20" style date. Events with an unparseable date fall into a
     * trailing "Scheduled" bucket so they're still visible.
     */
    private fun buildRows(events: List<CalendarEvent>): List<CalendarRow> {
        if (events.isEmpty()) return emptyList()

        val isoParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val headerFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        fun dayKey(cal: Calendar): Long {
            val c = cal.clone() as Calendar
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        val todayCal = Calendar.getInstance()
        val todayKey = dayKey(todayCal)
        val dayMs = 24L * 60 * 60 * 1000

        val rows = mutableListOf<CalendarRow>()
        var lastLabel: String? = null
        val undated = mutableListOf<CalendarEvent>()

        for (event in events) {
            val raw = event.date?.take(10)
            val parsed = raw?.let { runCatching { isoParser.parse(it) }.getOrNull() }
            if (parsed == null) {
                undated += event
                continue
            }
            val cal = Calendar.getInstance().apply { time = parsed }
            val key = dayKey(cal)
            val label = when ((key - todayKey) / dayMs) {
                0L -> getString(R.string.calendar_today)
                -1L -> getString(R.string.calendar_yesterday)
                1L -> getString(R.string.calendar_tomorrow)
                else -> headerFmt.format(parsed)
            }
            if (label != lastLabel) {
                rows += CalendarRow.Header(label)
                lastLabel = label
            }
            rows += CalendarRow.Event(event)
        }

        if (undated.isNotEmpty()) {
            rows += CalendarRow.Header(getString(R.string.calendar_scheduled))
            undated.forEach { rows += CalendarRow.Event(it) }
        }
        return rows
    }

    private fun openEvent(event: CalendarEvent) {
        val type = event.mediaType ?: return
        val item = event.item ?: return
        // Episodes/seasons aren't valid top-level detail resources — the API
        // only serves parent media types. Calendar episode rows carry the
        // show's media_id, so open it as the TV show instead.
        val openType = when (type) {
            MediaType.SEASON, MediaType.EPISODE -> MediaType.TV
            else -> type
        }
        val intent = Intent(requireContext(), MediaDetailsActivity::class.java).apply {
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_TYPE, openType.value)
            putExtra(MediaDetailsActivity.EXTRA_SOURCE, item.source)
            putExtra(MediaDetailsActivity.EXTRA_MEDIA_ID, item.mediaId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
