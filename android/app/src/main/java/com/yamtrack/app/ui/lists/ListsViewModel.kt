package com.yamtrack.app.ui.lists

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.CustomList
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListsViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _lists = MutableLiveData<Result<List<CustomList>>>()
    val lists: LiveData<Result<List<CustomList>>> = _lists

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _lists.value = Result.Loading
            _lists.value = repository.getLists(limit = 100)
        }
    }

    fun createList(name: String, description: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val r = repository.createList(name, description)) {
                is Result.Success -> refresh()
                is Result.Error -> _toast.value = r.message
                else -> {}
            }
        }
    }

    fun deleteList(listId: Long) {
        viewModelScope.launch {
            when (val r = repository.deleteList(listId)) {
                is Result.Success -> refresh()
                is Result.Error -> _toast.value = r.message
                else -> {}
            }
        }
    }

    fun toastShown() { _toast.value = null }
}

@HiltViewModel
class ListItemsViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _items = MutableLiveData<Result<List<MediaItem>>>()
    val items: LiveData<Result<List<MediaItem>>> = _items

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private var listId: Long = 0

    fun load(id: Long) {
        listId = id
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _items.value = Result.Loading
            _items.value = when (val r = repository.getListDetails(listId)) {
                is Result.Success -> Result.Success(r.data.items?.results ?: emptyList())
                is Result.Error -> r
                else -> Result.Error("Unknown error")
            }
        }
    }

    fun removeItem(item: MediaItem) {
        val type = item.mediaType ?: return
        viewModelScope.launch {
            when (val r = repository.removeMediaFromList(
                type, item.source, item.mediaId, listId
            )) {
                is Result.Success -> refresh()
                is Result.Error -> _toast.value = r.message
                else -> {}
            }
        }
    }

    fun toastShown() { _toast.value = null }
}
