package de.luma.gallery.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
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
@Config(sdk=[29],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisStoreTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var folder:File
    private lateinit var media:List<GalleryMedia>
    @Before fun setup() {
        context.deleteDatabase("gimbo-analysis.db")
        folder=File(context.cacheDir,"analysis-resume-test").apply {mkdirs()}
        val bitmap=Bitmap.createBitmap(40,32,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.RED)}
        val first=File(folder,"first.png");first.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val second=File(folder,"second.png").apply {writeBytes(first.readBytes())}
        bitmap.eraseColor(Color.BLUE)
        val third=File(folder,"third.png");third.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        media=listOf(first,second,third).map {GalleryMedia(it.name,Uri.fromFile(it),it.name,"Camera",0,size=it.length())}
    }
    @After fun cleanup() {folder.deleteRecursively();context.deleteDatabase("gimbo-analysis.db")}
    @Test fun interruptedHashingResumesFromDurableCheckpointsAndRechecksBeforeDeletion():Unit=runBlocking {
        val task=AnalysisStore(context).use {store->
            val task=store.create(AnalysisKind.DUPLICATES,media)
            store.transition(task,AnalysisStatus.RUNNING)
            try {DuplicateScanner.scan(context,media,false,store.checkpoints(task)) {comparing,done,total->
                store.progress(task,comparing,done,total);if(done==2) throw CancellationException("Process stopped")
            };fail()} catch(_:CancellationException) {}
            task
        }
        File(folder,"first.png").delete()
        AnalysisStore(context).use {store->
            assertEquals(2,store.task(task.kind)!!.done)
            val report=DuplicateScanner.scan(context,store.sources(task),false,store.checkpoints(task)) {_,_,_->}
            assertEquals(3,report.checked);assertEquals(0,report.unavailable)
            store.complete(task,report)
            assertTrue(runCatching {DuplicateScanner.verify(context,report,setOf("second.png"))}.isFailure)
        }
        AnalysisStore(context).use {store->
            val restored=store.snapshot(task.kind)
            assertEquals(AnalysisStatus.COMPLETE,restored.task!!.status)
            assertEquals(setOf("first.png","second.png"),restored.report!!.groups.single().files.map {it.media.id}.toSet())
        }
    }
    @Test fun visualFingerprintsAlsoSurviveAnInterruptedComparison():Unit=runBlocking {
        val task=AnalysisStore(context).use {store->
            val task=store.create(AnalysisKind.DUPLICATES,media,true)
            try {DuplicateScanner.scan(context,media,true,store.checkpoints(task)) {comparing,done,total->if(comparing && done==total) throw CancellationException()};fail()}
            catch(_:CancellationException) {}
            task
        }
        folder.listFiles()!!.forEach {it.delete()}
        AnalysisStore(context).use {store->
            val report=DuplicateScanner.scan(context,store.sources(task),true,store.checkpoints(task)) {_,_,_->}
            assertEquals(3,report.checked);assertEquals(0,report.unavailable);assertEquals(DuplicateKind.EXACT,report.groups.single().kind)
        }
    }
    @Test fun explicitPauseCannotBeOverwrittenByALateCompletion() {
        AnalysisStore(context).use {store->
            val task=store.create(AnalysisKind.DUPLICATES,media)
            assertTrue(store.transition(task,AnalysisStatus.PAUSED))
            store.complete(task,DuplicateReport(emptyList(),3,0))
            assertEquals(AnalysisStatus.PAUSED,store.snapshot(task.kind).task!!.status)
            assertNull(store.snapshot(task.kind).report)
            assertTrue(store.transition(store.task(task.kind)!!,AnalysisStatus.QUEUED))
            assertEquals(media,store.sources(task))
        }
    }
    @Test fun replacingAScanDoesNotAcceptOldResultsOrEraseAnotherKind() {
        AnalysisStore(context).use {store->
            val old=store.create(AnalysisKind.DUPLICATES,media)
            val mood=store.create(AnalysisKind.MOODS,media)
            val current=store.create(AnalysisKind.DUPLICATES,media.take(2),true,true)
            store.complete(old,DuplicateReport(emptyList(),3,0))
            assertEquals(current,store.task(AnalysisKind.DUPLICATES))
            assertEquals(mood,store.task(AnalysisKind.MOODS))
        }
    }
    @Test fun largeDuplicateFamiliesAvoidSingleCursorWindowSizedResultBlobs() {
        val files=(0 until 700).map {index->DuplicateFile(GalleryMedia("id:$index",Uri.parse("content://media/external/images/media/$index"),"a".repeat(3500),"Camera",0),"a".repeat(64),123)}
        val report=DuplicateReport(listOf(DuplicateGroup("large",DuplicateKind.EXACT,files)),700,0)
        AnalysisStore(context).use {store->val task=store.create(AnalysisKind.DUPLICATES,files.map {it.media});store.complete(task,report)}
        AnalysisStore(context).use {assertEquals(report,it.snapshot(AnalysisKind.DUPLICATES).report)}
    }
}
