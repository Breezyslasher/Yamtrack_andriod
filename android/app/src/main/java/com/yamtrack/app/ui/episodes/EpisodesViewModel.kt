package com.yamtrack.app.ui.episodes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamtrack.app.data.model.MediaDetails
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.model.WatchProvider
import com.yamtrack.app.data.model.WatchProviders
import com.yamtrack.app.links.ProviderLinks
import com.yamtrack.app.data.repository.PreferencesManager
import com.yamtrack.app.data.repository.YamtrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lists a season's episodes, their metadata, and watched state. */
@HiltViewModel
class EpisodesViewModel @Inject constructor(
    private val repository: YamtrackRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    suspend fun hideUnwatchedEpisodeInfo(): Boolean =
        preferencesManager.hideUnwatchedEpisodeInfo.first()

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

    /**
     * Where to watch one episode. [showLevel] holds links that only open the
     * show; [fromShow] is true when JustWatch has no links for the episode and
     * the show's providers are offered instead. [regionLink] is TMDB's page.
     */
    data class WatchOptions(
        val providers: List<WatchProvider>,
        val showLevel: Set<String>,
        val fromShow: Boolean,
        val regionLink: String?
    )

    private val episodeProviders = HashMap<Int, WatchProviders>()
    private var showProviders: WatchProviders? = null

    private suspend fun providersFor(episode: Int): WatchProviders? {
        episodeProviders[episode]?.let { return it }
        val r = repository.getEpisodeProviders(source, mediaId, seasonNumber, episode)
        return (r as? Result.Success)?.data?.also { episodeProviders[episode] = it }
    }

    private fun WatchProviders.all(): List<WatchProvider> = noCharge.orEmpty() + paid.orEmpty()

    /**
     * Loads [episode]'s providers, comparing with [neighbour] (the next, else
     * previous, episode): a link both share is the show's, not the episode's.
     * Null when the request fails.
     */
    suspend fun watchOptions(episode: Int, neighbour: Int?): WatchOptions? {
        val own = providersFor(episode) ?: return null
        val ownList = own.all()
        if (ownList.none { it.tvLink != null || it.link != null }) {
            // JustWatch doesn't have the episode (or the server has no episode
            // endpoint): the show's links, which open the show.
            val show = showProviders ?: (repository.getProviders(MediaType.TV, source, mediaId) as? Result.Success)
                ?.data?.also { showProviders = it }
            return WatchOptions(show?.all().orEmpty(), emptySet(), fromShow = true, regionLink = show?.link ?: own.link)
        }
        val shared = neighbour?.let { n -> providersFor(n)?.let { ProviderLinks.sharedLinks(ownList, it.all()) } }
        return WatchOptions(ownList, shared.orEmpty(), fromShow = false, regionLink = own.link)
    }
}
