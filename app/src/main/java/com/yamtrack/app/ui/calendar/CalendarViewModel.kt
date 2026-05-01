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
            // Default window: server uses the current month. Pull a larger
            // limit so a full month with daily releases still fits.
            _events.value = repository.getCalendar(limit = 200)
                .let { res ->
                    if (res is Result.Success) {
                        Result.Success(res.data.sortedBy { it.date.orEmpty() })
                    } else res
                }
            _isLoading.value = false
        }
    }
}
