package com.yamtrack.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    /**
     * Recently-updated items grouped by media type. Insertion order is the
     * canonical `MediaType.parentTypes` order so the home page renders the
     * same section order regardless of how the API returned the items.
     */
    private val _recentByType = MutableLiveData<Map<MediaType, List<MediaItem>>>(emptyMap())
    val recentByType: LiveData<Map<MediaType, List<MediaItem>>> = _recentByType

    private val _planningByType = MutableLiveData<Map<MediaType, List<MediaItem>>>(emptyMap())
    val planningByType: LiveData<Map<MediaType, List<MediaItem>>> = _planningByType

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true

            val recentDeferred = async {
                repository.getAllMedia(sort = "updated_desc", limit = 200)
            }
            val planningDeferred = async {
                repository.getAllMedia(
                    status = MediaStatus.PLANNING,
                    sort = "updated_desc",
                    limit = 200
                )
            }

            _recentByType.value = groupByType(recentDeferred.await())
            _planningByType.value = groupByType(planningDeferred.await())

            _isLoading.value = false
        }
    }

    /** Group by parent media type, preserve API ordering inside each group. */
    private fun groupByType(result: Result<List<MediaItem>>): Map<MediaType, List<MediaItem>> {
        val items = (result as? Result.Success)?.data ?: return emptyMap()
        val groups = LinkedHashMap<MediaType, MutableList<MediaItem>>()
        MediaType.parentTypes.forEach { groups[it] = mutableListOf() }
        items.forEach { item ->
            val type = item.mediaType ?: return@forEach
            groups[type]?.add(item)
        }
        return groups.filterValues { it.isNotEmpty() }
    }
}
