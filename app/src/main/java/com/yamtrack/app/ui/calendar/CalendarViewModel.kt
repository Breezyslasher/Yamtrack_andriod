package com.yamtrack.app.ui.calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.CalendarEvent
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _events = MutableLiveData<Result<List<CalendarEvent>>>()
    val events: LiveData<Result<List<CalendarEvent>>> = _events

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _events.value = Result.Loading

            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = Calendar.getInstance()
            val startStr = iso.format(today.time)
            val end = (today.clone() as Calendar).apply { add(Calendar.MONTH, 3) }
            val endStr = iso.format(end.time)
            val todayKey = startStr  // yyyy-MM-dd compares lexicographically

            _events.value = repository.getCalendar(
                startDate = startStr,
                endDate = endStr,
                limit = 200
            ).let { res ->
                if (res is Result.Success) {
                    // Only keep releases that are today or later; the server
                    // range can still include earlier same-month entries.
                    val upcoming = res.data
                        .filter { (it.date?.take(10) ?: "") >= todayKey }
                        .sortedBy { it.date.orEmpty() }
                    Result.Success(upcoming)
                } else res
            }
            _isLoading.value = false
        }
    }
}
