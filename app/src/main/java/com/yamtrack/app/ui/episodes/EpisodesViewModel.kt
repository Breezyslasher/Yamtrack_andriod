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

/** Read-only: lists a season's episodes and their metadata. */
@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val repository: YamtrackRepository
) : ViewModel() {

    private val _episodes = MutableLiveData<Result<List<MediaItem>>>()
    val episodes: LiveData<Result<List<MediaItem>>> = _episodes

    private var source = ""
    private var mediaId = ""
    private var seasonNumber = 0

    fun load(source: String, mediaId: String, seasonNumber: Int) {
        this.source = source
        this.mediaId = mediaId
        this.seasonNumber = seasonNumber
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
}
