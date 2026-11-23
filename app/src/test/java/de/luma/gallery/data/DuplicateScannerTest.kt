package de.luma.gallery.data

import android.graphics.*
import android.net.Uri
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DuplicateScannerTest {
    private lateinit var folder:File
    private lateinit var files:List<File>
    private lateinit var media:List<GalleryMedia>
    @Before fun sources() {
        folder=File(RuntimeEnvironment.getApplication().cacheDir,"duplicate-test").apply {mkdirs()}
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {drawColor(Color.BLUE);drawRect(4f,4f,32f,60f,Paint().apply {color=Color.RED});drawRect(32f,24f,60f,40f,Paint().apply {color=Color.YELLOW})}
        val first=File(folder,"a.png");first.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val copy=File(folder,"b.png").apply {writeBytes(first.readBytes())}
        val jpeg=File(folder,"c.jpg");jpeg.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)}
        bitmap.eraseColor(Color.GREEN)
        val different=File(folder,"d.png");different.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        files=listOf(first,copy,jpeg,different)
        media=files.map {GalleryMedia(it.name,Uri.fromFile(it),it.name,"Camera",0,size=it.length())}
    }
    @After fun cleanup() {folder.deleteRecursively()}
    @Test fun exactScanUsesFileContentsAndDeduplicatesRepeatedSourceUris():Unit=runBlocking {
        val report=DuplicateScanner.scan(RuntimeEnvironment.getApplication(),media+media.first(),false) {_,_,_->}
        assertEquals(4,report.checked);assertEquals(1,report.groups.size);assertEquals(DuplicateKind.EXACT,report.groups.single().kind)
        assertEquals(setOf("a.png","b.png"),report.groups.single().files.map {it.media.id}.toSet())
        assertEquals(setOf("b.png"),report.exactExtras())
    }
    @Test fun visualScanFindsEncodedVariantsAndKeepsUnrelatedColorsSeparate():Unit=runBlocking {
        val report=DuplicateScanner.scan(RuntimeEnvironment.getApplication(),media,true) {_,_,_->}
        assertEquals(1,report.groups.size)
        val source=PhotoEngine.decode(RuntimeEnvironment.getApplication(),media[0].uri,96)
        val variant=PhotoEngine.decode(RuntimeEnvironment.getApplication(),media[2].uri,96)
        val a=DuplicateScanner.fingerprint(source);val b=DuplicateScanner.fingerprint(variant)
        source.recycle();variant.recycle()
        assertTrue("Encoded variant should match; difference hash distance ${java.lang.Long.bitCount(a.hash xor b.hash)}",DuplicateScanner.looksSimilar(a,b))
        assertEquals(DuplicateKind.SIMILAR,report.groups.single().kind)
        assertEquals(setOf("a.png","b.png","c.jpg"),report.groups.single().files.map {it.media.id}.toSet())
        assertTrue(report.exactExtras().isEmpty())
    }
    @Test fun verificationRejectsAllCopiesAndChangedOrMissingKeepers():Unit=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val report=DuplicateScanner.scan(context,media,false) {_,_,_->}
        assertTrue(runCatching {report.safeSelection(setOf("a.png","b.png"))}.isFailure)
        assertEquals(listOf(media[1]),DuplicateScanner.verify(context,report,setOf("b.png")))
        files[1].appendBytes(byteArrayOf(42))
        assertTrue(runCatching {DuplicateScanner.verify(context,report,setOf("b.png"))}.isFailure)
        files[1].writeBytes(files[0].readBytes());files[0].delete()
        assertTrue(runCatching {DuplicateScanner.verify(context,report,setOf("b.png"))}.isFailure)
        assertTrue(files[1].exists())
    }
    @Test fun sameDifferenceHashDoesNotMakeDifferentSolidColorsSimilar() {
        val red=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        val blue=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
        val a=DuplicateScanner.fingerprint(red);val b=DuplicateScanner.fingerprint(blue)
        assertEquals(a.hash,b.hash);assertFalse(DuplicateScanner.looksSimilar(a,b));red.recycle();blue.recycle()
    }
    @Test fun cancellationStopsReadingFurtherFilesWithoutChangingSources():Unit=runBlocking {
        var checked=0;var result:DuplicateReport?=null
        lateinit var job:Job
        job=launch {result=DuplicateScanner.scan(RuntimeEnvironment.getApplication(),media,true) {_,done,_->checked=done;job.cancel()}}
        job.join();assertNull(result);assertEquals(1,checked)
        assertTrue(files.all {it.exists()});assertArrayEquals(files[0].readBytes(),files[1].readBytes())
    }
}
