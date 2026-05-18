package com.yamtrack.app.ui.episodes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaStatus
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.UpdateMediaRequest
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _episodes = MutableLiveData<Result<List<MediaItem>>>()
    val episodes: LiveData<Result<List<MediaItem>>> = _episodes

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private var source = ""
    private var mediaId = ""
    private var seasonNumber = 0

    fun load(source: String, mediaId: String, seasonNumber: Int) {
        this.source = source
        this.mediaId = mediaId
        this.seasonNumber = seasonNumber
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _episodes.value = Result.Loading
            _episodes.value = repository.getEpisodes(source, mediaId, seasonNumber)
        }
    }

    suspend fun episodeDetail(episodeNumber: Int): MediaDetails? =
        when (val r = repository.getEpisodeDetails(source, mediaId, seasonNumber, episodeNumber)) {
            is Result.Success -> r.data
            else -> null
        }

    fun setEpisodeWatched(episodeNumber: Int, watched: Boolean) {
        viewModelScope.launch {
            val status = if (watched) MediaStatus.COMPLETED else MediaStatus.PLANNING
            val r = repository.updateEpisode(
                source, mediaId, seasonNumber, episodeNumber,
                UpdateMediaRequest(status = status.code)
            )
            _message.value = (r as? Result.Error)?.message ?: "Updated"
            if (r is Result.Success) refresh()
        }
    }

    fun markSeasonWatched() {
        viewModelScope.launch {
            val r = repository.updateSeason(
                source, mediaId, seasonNumber,
                UpdateMediaRequest(status = MediaStatus.COMPLETED.code)
            )
            _message.value = (r as? Result.Error)?.message ?: "Season marked watched"
            if (r is Result.Success) refresh()
        }
    }
}
