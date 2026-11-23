package de.luma.gallery.model

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class MediaExtrasTest {
    private fun photo(name:String,album:String="Camera",date:LocalDate?=null,video:Boolean=false)=GalleryMedia(name,Uri.parse("content://media/external/${if(video) "video" else "images"}/media/1"),name,album,date?.atStartOfDay(ZoneId.of("Europe/Vienna"))?.toInstant()?.toEpochMilli() ?: 0,isVideo=video)
    @Test fun screenshotsAreRecognizedWithoutMovingOrMisclassifyingVideos() {
        val screenshots=listOf(photo("Screenshot_20261012.png"),photo("IMG_42.jpg","Screenshots"),photo("Bildschirmfoto 2026-10-12.png"),photo("Captura de pantalla 42.jpg"),photo("screenshot2026.png"))
        assertTrue(screenshots.all {it.isScreenshot})
        val others=listOf(photo("IMG_42.jpg"),photo("myscreenshotstory.jpg"),photo("Screenshot_42.mp4",video=true))
        assertTrue(others.none {it.isScreenshot})
        assertEquals(screenshots.toSet(),selectMedia(screenshots+others,MediaFilter.SCREENSHOTS,emptySet()).toSet())
    }
    @Test fun memoriesRespectLocalDatesMissingDatesAndScreenshotExclusion() {
        val today=LocalDate.of(2026,10,12);val zone=ZoneId.of("Europe/Vienna")
        val old=photo("old.jpg",date=LocalDate.of(2025,10,12));val second=photo("second.jpg",date=LocalDate.of(2025,10,11));val future=photo("future.jpg",date=today.plusDays(1));val screenshot=photo("Screenshot_1.png",date=LocalDate.of(2025,10,12))
        val memories=mediaMemories(listOf(old,second,future,screenshot,photo("unknown.jpg")),today,zone)
        assertEquals(listOf(old),memories.first().media)
        assertTrue(memories.any {it.media.toSet()==setOf(old,second)})
        assertTrue(memories.flatMap {it.media}.none {it==future || it==screenshot || it.timestamp==0L})
        assertEquals(memories.size,memories.map {it.media.map {m->m.id}.toSet()}.distinct().size)
    }
    @Test fun reviewUndoRestoresTheCorrectPhotoAndKeepsDecisionsSeparateUntilApplied() {
        var session=ReviewSession(listOf("first","second","third"))
        session=session.choose(ReviewAction.REMOVE).choose(ReviewAction.KEEP)
        assertEquals("third",session.currentId);assertEquals(setOf("first"),session.selected(ReviewAction.REMOVE))
        session=session.undo();assertEquals("second",session.currentId)
        session=session.choose(ReviewAction.FAVORITE).choose(ReviewAction.ARCHIVE)
        assertTrue(session.finished);assertNull(session.currentId)
        assertEquals(setOf("second"),session.selected(ReviewAction.FAVORITE));assertEquals(setOf("third"),session.selected(ReviewAction.ARCHIVE))
        assertEquals(session,session.choose(ReviewAction.REMOVE))
        assertTrue(runCatching {ReviewSession(listOf("same","same"))}.isFailure)
    }
    @Test fun everyCollageLayoutHasExactlyOneNonoverlappingCellPerPhoto() {
        for(count in 2..9) for(layout in CollageLayout.entries) for(ratio in listOf(.5625f,.8f,1f,16f/9)) {
            val cells=CollageSpec(layout=layout,ratio=ratio,gap=.05f,tiles=List(count) {CollageTile()}).cells()
            assertEquals(count,cells.size)
            cells.forEach {assertTrue(it.left>=0 && it.top>=0 && it.right<=1 && it.bottom<=1 && it.right>it.left && it.bottom>it.top)}
            cells.forEachIndexed {i,a->cells.drop(i+1).forEach {b->assertTrue(a.right<=b.left || b.right<=a.left || a.bottom<=b.top || b.bottom<=a.top)}}
        }
    }
}
