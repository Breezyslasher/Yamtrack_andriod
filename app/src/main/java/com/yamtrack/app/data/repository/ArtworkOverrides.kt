package com.yamtrack.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.ConcurrentHashMap

/**
 * User-chosen artwork per media item, stored on-device.
 *
 * The API exposes the list of available images but has no field to persist
 * a custom one, so the choice lives here and is applied wherever the app
 * renders that item's image (model getters read through [resolve]).
 */
object ArtworkOverrides {

    private const val PREFS = "artwork_overrides"

    private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, String>()

    private val _version = MutableLiveData(0)
    /** Bumps on every change so on-screen lists can rebind. */
    val version: LiveData<Int> = _version

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        cache.clear()
        p.all.forEach { (k, v) -> if (v is String) cache[k] = v }
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

    /** Returns the override for [key] if one is set, otherwise [fallback]. */
    fun resolve(key: String?, fallback: String?): String? = get(key) ?: fallback

    fun set(key: String, url: String) {
        cache[key] = url
        prefs?.edit()?.putString(key, url)?.apply()
        bump()
    }

    fun clear(key: String) {
        cache.remove(key)
        prefs?.edit()?.remove(key)?.apply()
        bump()
    }

    private fun bump() {
        _version.postValue((_version.value ?: 0) + 1)
    }
}
