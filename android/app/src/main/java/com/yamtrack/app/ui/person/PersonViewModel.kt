package com.yamtrack.app.ui.person

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.KnownFor
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Person
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _person = MutableLiveData<Result<Person>>()
    val person: LiveData<Result<Person>> = _person

    private val _knownFor = MutableLiveData<Result<List<KnownFor>>>()
    val knownFor: LiveData<Result<List<KnownFor>>> = _knownFor

    private var source = ""
    private var personId = 0L
    private var knownForJob: Job? = null
    private var loadedFor: Pair<String, Long>? = null

    fun load(source: String, personId: Long) {
        if (loadedFor == source to personId) return
        loadedFor = source to personId
        this.source = source
        this.personId = personId
        viewModelScope.launch {
            _person.value = Result.Loading
            _person.value = repository.getPerson(source, personId)
        }
        loadKnownFor(null)
    }

    /** [mediaType] null = movies and tv together. */
    fun loadKnownFor(mediaType: MediaType?) {
        knownForJob?.cancel()
        knownForJob = viewModelScope.launch {
            _knownFor.value = Result.Loading
            _knownFor.value = when (
                val r = repository.getPersonKnownFor(source, personId, mediaType)
            ) {
                // Newest first; undated (announced) titles go to the top.
                is Result.Success -> Result.Success(
                    r.data.sortedByDescending { it.releaseDate ?: "9999" }
                )
                else -> r
            }
        }
    }
}
