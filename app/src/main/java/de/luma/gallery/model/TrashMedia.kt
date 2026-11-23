package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
data class TrashMedia(val media: GalleryMedia, val expiresAt: Long? = null)
data class TrashSnapshot(val items: List<TrashMedia>, val accessLimited: Boolean)

enum class MediaAction { TRASH, RESTORE, DELETE }
data class PendingMediaAction(val action: MediaAction, val items: List<GalleryMedia>)


fun canManageMedia(item: GalleryMedia): Boolean {
    val uri = item.uri
    val path = uri.pathSegments
    return !item.isDemo && uri.scheme == "content" && uri.authority == "media" &&
        uri.query == null && uri.fragment == null && path.size == 4 &&
        path[0] != "internal" && path[0].isNotBlank() &&
        path[1] == (if (item.isVideo) "video" else "images") && path[2] == "media" &&
        (path[3].toLongOrNull() ?: 0) > 0
}

const val MEDIA_ACTION_LIMIT = 200

fun checkedMediaAction(action: MediaAction, items: List<GalleryMedia>): PendingMediaAction {
    requireMessage(items.isNotEmpty(), R.string.select_media_first)
    requireMessage(items.size <= MEDIA_ACTION_LIMIT, R.string.invalid_media_limit, MEDIA_ACTION_LIMIT)
    requireMessage(items.all(::canManageMedia), R.string.local_media_only)
    requireMessage(items.map { it.uri }.distinct().size == items.size, R.string.duplicate_media_selection)
    return PendingMediaAction(action, items.toList())
}
