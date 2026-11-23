package de.luma.gallery.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaQueryTest {
    private val photos = listOf(
        GalleryMedia("a", Uri.EMPTY, "Alpenglühen.jpg", "Berge", 1791583200000L),
        GalleryMedia("b", Uri.EMPTY, "Strand.mp4", "Reise", 1791669600000L, isVideo = true),
        GalleryMedia("c", Uri.EMPTY, "Morgen.png", "Berge", 1791756000000L),
    )

    @Test fun searchIgnoresCaseAndAccentsAndCombinesWords() {
        assertEquals(listOf("a"), selectMedia(photos, MediaFilter.ALL, emptySet(), "ALPENGLUHEN berge").map { it.id })
        assertTrue(selectMedia(photos, MediaFilter.ALL, emptySet(), "Alpenglühen Reise").isEmpty())
        assertEquals(3, selectMedia(photos, MediaFilter.ALL, emptySet(), "Oktober 2026", locale = java.util.Locale.GERMAN).size)
    }

    @Test fun favoritesTypeAlbumAndSortWorkTogether() {
        assertEquals(listOf("c", "a"), selectMedia(photos, MediaFilter.FAVORITES, setOf("a", "c")).map { it.id })
        assertEquals(listOf("a", "c"), selectMedia(photos, MediaFilter.PHOTOS, emptySet(), album = "Berge", newestFirst = false).map { it.id })
        assertEquals(listOf("b"), selectMedia(photos, MediaFilter.VIDEOS, emptySet()).map { it.id })
    }
}
