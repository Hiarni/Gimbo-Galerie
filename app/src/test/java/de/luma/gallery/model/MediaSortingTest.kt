package de.luma.gallery.model

import android.net.Uri
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MediaSortingTest {
    private val media=listOf(
        GalleryMedia("b",Uri.EMPTY,"Z.jpg","Camera",1,size=300),
        GalleryMedia("a",Uri.EMPTY,"Ä.jpg","Family",2,size=100),
        GalleryMedia("c",Uri.EMPTY,"Video.mp4","Camera",3,true,2000,200))
    @Test fun criteriaDirectionsLocaleAndFiltersStayIndependent() {
        fun sorted(sort:MediaSort,descending:Boolean=false)=selectMedia(media,MediaFilter.ALL,emptySet(),newestFirst=descending,locale=Locale.GERMAN,sort=sort).map {it.id}
        assertEquals(listOf("a","c","b"),sorted(MediaSort.NAME))
        assertEquals(listOf("c","a","b"),sorted(MediaSort.DATE,true))
        assertEquals(listOf("a","c","b"),sorted(MediaSort.SIZE))
        assertEquals(listOf("c","a","b"),sorted(MediaSort.DURATION,true))
        assertEquals(listOf("a"),selectMedia(media,MediaFilter.FAVORITES,setOf("a"),sort=MediaSort.SIZE).map {it.id})
        assertEquals(listOf("b"),selectMedia(media,MediaFilter.PHOTOS,emptySet(),album="Camera",sort=MediaSort.NAME).map {it.id})
    }
    @Test fun equalPrimaryValuesHaveStableIdentityOrder() {
        val tied=media.map {it.copy(timestamp=0)}
        assertEquals(listOf("a","b","c"),selectMedia(tied,MediaFilter.ALL,emptySet()).map {it.id})
        assertEquals(listOf("a","b","c"),selectMedia(tied.reversed(),MediaFilter.ALL,emptySet(),newestFirst=false).map {it.id})
    }
}
