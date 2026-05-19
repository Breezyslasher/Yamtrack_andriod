package com.yamtrack.app.ui.episodes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lists a season's episodes, their metadata, and watched state. */
@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _episodes = MutableLiveData<Result<List<MediaItem>>>()
    val episodes: LiveData<Result<List<MediaItem>>> = _episodes

    private val _toast = MutableLiveData<String?>()
    val toast: LiveData<String?> = _toast

    private var source = ""
    private var mediaId = ""
    private var seasonNumber = 0

    fun load(source: String, mediaId: String, seasonNumber: Int) {
        this.source = source
        this.mediaId = mediaId
        this.seasonNumber = seasonNumber
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch {
            _episodes.value = Result.Loading
            _episodes.value = repository.getEpisodes(source, mediaId, seasonNumber)
        }
    }

    fun setEpisodeWatched(episodeNumber: Int, watched: Boolean) {
        viewModelScope.launch {
            when (val r = repository.setEpisodeWatched(
                source, mediaId, seasonNumber, episodeNumber, watched
            )) {
                is Result.Success -> refresh()
                is Result.Error -> {
                    _toast.value = r.message
                    refresh() // resync checkbox to server truth
                }
                else -> {}
            }
        }
    }

    fun toastShown() { _toast.value = null }

    suspend fun episodeDetail(episodeNumber: Int): MediaDetails? =
        when (val r = repository.getEpisodeDetails(source, mediaId, seasonNumber, episodeNumber)) {
            is Result.Success -> r.data
            else -> null
        }
}
