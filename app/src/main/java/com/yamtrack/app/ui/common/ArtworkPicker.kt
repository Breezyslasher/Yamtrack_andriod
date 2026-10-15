package com.yamtrack.app.ui.common

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yamtrack.app.R
import com.yamtrack.app.data.model.Cover
import com.yamtrack.app.data.model.MediaImage
import com.yamtrack.app.data.model.MediaItem
import com.yamtrack.app.data.model.MediaType
import com.yamtrack.app.data.model.Result
import com.yamtrack.app.data.repository.ArtworkOverrides
import com.yamtrack.app.data.repository.YamtrackRepository
import com.yamtrack.app.ui.details.ArtworkAdapter
import com.yamtrack.app.widget.CalendarWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Change artwork" dialog, usable from any screen. The picked cover is
 * saved on the server (shared by every user and the web app), then every
 * list on screen rebinds through [ArtworkOverrides].
 */
class ArtworkPicker @Inject constructor(
    private val repository: YamtrackRepository
) {

    /** What the cover belongs to: a media item, or a tv season. */
    data class Target(
        val mediaType: MediaType,
        val source: String,
        val mediaId: String,
        val seasonNumber: Int? = null
    ) {
        val cacheKey: String?
            get() = if (seasonNumber != null) {
                ArtworkOverrides.key(MediaType.SEASON.value, source, mediaId, seasonNumber)
            } else {
                ArtworkOverrides.key(mediaType.value, source, mediaId)
            }

        companion object {
            /** Null for items that have no cover of their own (episodes). */
            fun of(item: MediaItem): Target? {
                val type = item.mediaType ?: return null
                val source = item.source.takeIf { it.isNotBlank() } ?: return null
                val mediaId = item.mediaId.takeIf { it.isNotBlank() } ?: return null
                return when (type) {
                    MediaType.EPISODE -> null
                    MediaType.SEASON -> item.item?.seasonNumber?.let {
                        Target(MediaType.TV, source, mediaId, it)
                    }
                    else -> Target(type, source, mediaId)
                }
            }
        }
    }

    /** Fetches the options, then shows the picker. [scope] should be tied to [context]'s lifecycle. */
    fun show(context: Context, scope: CoroutineScope, target: Target) {
        scope.launch {
            val cover = async {
                repository.getCover(target.mediaType, target.source, target.mediaId, target.seasonNumber)
            }
            val images = repository.getArtworkImages(
                target.mediaType, target.source, target.mediaId, target.seasonNumber
            )
            if (images is Result.Error) {
                cover.cancel()
                toast(context, images.message)
                return@launch
            }
            val data = (images as? Result.Success)?.data ?: return@launch

            // Poster-shaped options first; any image when there are no posters.
            val options = data.posters.orEmpty().ifEmpty {
                data.backdrops.orEmpty() + data.screenshots.orEmpty()
            }.filter { !(it.image ?: it.original).isNullOrBlank() }
            val custom = (cover.await() as? Result.Success)?.data?.customImage

            if (options.isEmpty() && custom == null) {
                toast(context, context.getString(R.string.artwork_none))
                return@launch
            }
            showDialog(context, scope, target, options, custom)
        }
    }

    private fun showDialog(
        context: Context,
        scope: CoroutineScope,
        target: Target,
        options: List<MediaImage>,
        custom: String?
    ) {
        var dialog: AlertDialog? = null
        val pad = (context.resources.displayMetrics.density * 12).toInt()
        val grid = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            setPadding(pad, pad, pad, 0)
            clipToPadding = false
            adapter = ArtworkAdapter(options, custom) onPick@{ picked: MediaImage ->
                val url = picked.image ?: picked.original ?: return@onPick
                dialog?.dismiss()
                save(context, scope, target, url)
            }
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(
                if (target.seasonNumber != null) R.string.change_season_cover
                else R.string.change_artwork
            )
            .setView(grid)
            .setNegativeButton(R.string.cancel, null)
        if (custom != null) {
            builder.setNeutralButton(R.string.reset_artwork) { _, _ ->
                save(context, scope, target, null)
            }
        }
        dialog = builder.show()
    }

    /** [url] null resets the cover to the provider image. */
    private fun save(context: Context, scope: CoroutineScope, target: Target, url: String?) {
        scope.launch {
            val result: Result<Cover> = if (url != null) {
                repository.setCover(
                    target.mediaType, target.source, target.mediaId, url, target.seasonNumber
                )
            } else {
                repository.resetCover(
                    target.mediaType, target.source, target.mediaId, target.seasonNumber
                )
            }
            when (result) {
                is Result.Success -> {
                    target.cacheKey?.let { ArtworkOverrides.set(it, result.data.image) }
                    CalendarWidgetProvider.requestRefresh(context)
                    toast(
                        context,
                        context.getString(
                            if (url != null) R.string.artwork_updated else R.string.artwork_reset
                        )
                    )
                }
                is Result.Error -> toast(
                    context,
                    context.getString(R.string.artwork_save_failed, result.message)
                )
                Result.Loading -> Unit
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
