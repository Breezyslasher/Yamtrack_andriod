package com.yamtrack.app.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.ConcurrentHashMap

/**
 * Covers changed during this session, keyed by media item.
 *
 * The cover itself is stored on the server (the `cover/` endpoints) and
 * comes back as the item's `image` on every later fetch. This in-memory
 * map only bridges the gap for lists that were loaded before the change,
 * so every screen shows the new cover right away without refetching.
 */
object ArtworkOverrides {

    /** Old on-device store from before covers were saved on the server. */
    private const val LEGACY_PREFS = "artwork_overrides"

    private val cache = ConcurrentHashMap<String, String>()

    private val _version = MutableLiveData(0)
    /** Bumps on every change so on-screen lists can rebind. */
    val version: LiveData<Int> = _version

    fun init(context: Context) {
        context.applicationContext.deleteSharedPreferences(LEGACY_PREFS)
    }

    fun key(
        mediaType: String?,
        source: String?,
        mediaId: String?,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? {
        if (mediaType.isNullOrBlank() || source.isNullOrBlank() || mediaId.isNullOrBlank()) {
            return null
        }
        return listOf(mediaType, source, mediaId, seasonNumber ?: "", episodeNumber ?: "")
            .joinToString("|")
    }

    fun get(key: String?): String? = key?.let { cache[it] }

    /** Returns the cover changed this session for [key], otherwise [fallback]. */
    fun resolve(key: String?, fallback: String?): String? = get(key) ?: fallback

    /** Records the cover the server now has for [key] (null = none known). */
    fun set(key: String, url: String?) {
        if (url.isNullOrBlank()) {
            if (cache.remove(key) == null) return
        } else {
            if (cache.put(key, url) == url) return
        }
        bump()
    }

    /**
     * Fresh server data for [key]: if this session changed its cover, follow
     * what the server says now (it may have been changed again elsewhere).
     */
    fun sync(key: String?, serverImage: String?) {
        if (key == null || !cache.containsKey(key)) return
        set(key, serverImage)
    }

    private fun bump() {
        _version.postValue((_version.value ?: 0) + 1)
    }
}
