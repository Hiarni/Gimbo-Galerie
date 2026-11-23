package de.luma.gallery.model

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun String.searchKey() = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .replace("ß", "ss")

fun selectMedia(
    media: List<GalleryMedia>,
    filter: MediaFilter,
    favorites: Set<String>,
    query: String = "",
    album: String? = null,
    newestFirst: Boolean = true,
    locale: Locale = Locale.getDefault(),
    sort: MediaSort = MediaSort.DATE,
): List<GalleryMedia> {
    val words = query.searchKey().split(Regex("\\s+")).filter { it.isNotBlank() }
    val date = SimpleDateFormat("yyyy-MM-dd dd.MM.yyyy MMMM yyyy", locale)
    val result = media.filter { item ->
        (album == null || item.album == album) &&
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !item.isVideo
                MediaFilter.VIDEOS -> item.isVideo
                MediaFilter.FAVORITES -> item.id in favorites
                MediaFilter.SCREENSHOTS -> item.isScreenshot
            } && words.all { word ->
                "${item.name} ${item.album} ${date.format(Date(item.timestamp))}".searchKey().contains(word)
            }
    }
    val collator=java.text.Collator.getInstance(locale).apply {strength=java.text.Collator.PRIMARY}
    val alphabet=Comparator<String> {a,b->collator.compare(a,b)}
    val primary: Comparator<GalleryMedia> = when(sort) {
        MediaSort.DATE -> compareBy {it.timestamp}
        MediaSort.NAME -> compareBy(alphabet) {it.name}
        MediaSort.SIZE -> compareBy {it.size}
        MediaSort.DURATION -> compareBy {it.durationMs}
        MediaSort.TYPE -> compareBy<GalleryMedia> {it.isVideo}.thenBy {it.name.substringAfterLast('.', "").lowercase(Locale.ROOT)}
        MediaSort.ALBUM -> compareBy(alphabet) {it.album}
    }
    return result.sortedWith((if(newestFirst) primary.reversed() else primary).thenBy {it.id})
}
