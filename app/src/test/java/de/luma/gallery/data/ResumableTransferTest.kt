package de.luma.gallery.data

import android.Manifest
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ResumableTransferTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private lateinit var provider: DocumentToolsTest.TestProvider
    private val tree get()=DocumentsContract.buildTreeDocumentUri("luma.test.docs","root")
    private fun uri(id: String)=DocumentsContract.buildDocumentUriUsingTree(tree,id)
    @Before fun setup(){val app=context;provider=DocumentToolsTest.TestProvider().apply{attachInfo(app,ProviderInfo().apply{authority="luma.test.docs";exported=true;grantUriPermissions=true;readPermission=Manifest.permission.MANAGE_DOCUMENTS;writePermission=Manifest.permission.MANAGE_DOCUMENTS})};ShadowContentResolver.registerProviderInternal("luma.test.docs",provider);TransferJournal(context).delete()}
    @After fun cleanup(){TransferJournal(context).delete();provider.folder.deleteRecursively()}
    private fun task(source: String="source",move: Boolean=false)=TransferTask(uri(source).toString(),uri("target").toString(),move,sourceParent=uri("root").toString())
    private fun engine()=ResumableTransfer(context){TransferJournal(context).write(it)}
    @Test fun restartAfterReservedDestinationReusesItAndVerifiesContent()=runBlocking {
        val task=task();var stopped=false
        assertTrue(runCatching {ResumableTransfer(context){TransferJournal(context).write(it);if(it.nodes.singleOrNull()?.target!=null && !stopped){stopped=true;throw java.io.IOException()}}.run(task){} }.isFailure)
        assertEquals(1,DocumentTools(context).children(uri("target")).size)
        engine().run(TransferJournal(context).read()!!){}
        assertEquals(1,DocumentTools(context).children(uri("target")).size)
        assertTrue(provider.records.containsKey("source"))
        val restored=TransferJournal(context).read()!!;assertNotNull(restored.nodes.single().hash)
    }
    @Test fun partiallyDeletedFolderResumesWithoutRecopyingOrRecursivelyDeleting()=runBlocking {
        provider.addFolder("folder","root","Folder");provider.add("a","folder","A.jpg",byteArrayOf(1));provider.add("b","folder","B.jpg",byteArrayOf(2));provider.blockedDeletes.add("a")
        assertTrue(runCatching{engine().run(task("folder",true)){} }.isFailure)
        assertFalse(provider.records.containsKey("b"));assertTrue(provider.records.containsKey("a"))
        val targets=provider.records.keys.filter{it.startsWith("new-")}.toSet()
        provider.blockedDeletes.clear();engine().run(TransferJournal(context).read()!!){}
        assertFalse(provider.records.containsKey("folder"));assertEquals(targets,provider.records.keys.filter{it.startsWith("new-")}.toSet())
    }
    @Test fun lossBetweenProviderDeletionAndJournalCommitReconcilesDeleteIntent()=runBlocking {
        val task=task(move=true);var failed=false
        assertTrue(runCatching{ResumableTransfer(context){if(it.nodes.singleOrNull()?.removed==true && !failed){failed=true;throw java.io.IOException()} else TransferJournal(context).write(it)}.run(task){} }.isFailure)
        assertFalse(provider.records.containsKey("source"));val checkpoint=TransferJournal(context).read()!!;assertTrue(checkpoint.nodes.single().deleting);assertFalse(checkpoint.nodes.single().removed)
        engine().run(checkpoint){};assertTrue(checkpoint.nodes.single().removed)
    }
    @Test fun changedSourceOrDamagedCopyBlocksRemainingDeletesOnResume()=runBlocking {
        provider.failDeleteSource=true;assertTrue(runCatching{engine().run(task(move=true)){} }.isFailure)
        provider.failDeleteSource=false;provider.records.getValue("source").file!!.writeBytes(byteArrayOf(42))
        assertTrue(runCatching{engine().run(TransferJournal(context).read()!!){} }.isFailure);assertTrue(provider.records.containsKey("source"))
    }
    @Test fun newChildBeforeResumeKeepsEveryRemainingOriginal()=runBlocking {
        provider.addFolder("folder","root","Folder");provider.add("a","folder","A.jpg",byteArrayOf(1));provider.blockedDeletes.add("a")
        assertTrue(runCatching{engine().run(task("folder",true)){} }.isFailure)
        provider.add("newchild","folder","Late.jpg",byteArrayOf(8));provider.blockedDeletes.clear()
        assertTrue(runCatching{engine().run(TransferJournal(context).read()!!){} }.isFailure)
        assertTrue(provider.records.containsKey("a"));assertTrue(provider.records.containsKey("newchild"))
    }
    @Test fun pauseAfterOneCopyRetainsCheckpointAndResumesOtherFiles()=runBlocking {
        provider.addFolder("folder","root","Folder");provider.add("a","folder","A.jpg",byteArrayOf(1));provider.add("b","folder","B.jpg",byteArrayOf(2))
        assertTrue(runCatching{engine().run(task("folder")){p->if(p.phase==DocumentTransferPhase.COPYING && p.completed==2)throw CancellationException()} }.isFailure)
        val previous=TransferJournal(context).read()!!;val completed=previous.nodes.count{it.hash!=null};assertEquals(1,completed)
        engine().run(previous){};assertEquals(2,previous.nodes.count{it.hash!=null});assertTrue(provider.records.containsKey("a"));assertTrue(provider.records.containsKey("b"))
    }
    @Test fun journalRejectsBadParentsUrisHashesAndDeleteStates(){
        val valid=task().apply{nodes+=TransferNode(uri("source").toString(),-1,"Photo.jpg","image/jpeg",0)}
        assertEquals(valid,TransferTask.read(valid.json()))
        assertTrue(runCatching{TransferTask.read(org.json.JSONObject(valid.json()).put("source","https://bad/source").toString())}.isFailure)
        assertTrue(runCatching{TransferTask.read(valid.json().replace("\"parent\":-1","\"parent\":0"))}.isFailure)
        valid.nodes.single().deleting=true;assertTrue(runCatching{TransferTask.read(valid.json())}.isFailure)
    }
    @Test fun targetInsideSourceIsRejectedBeforeAnyDestinationIsCreated()=runBlocking {
        provider.addFolder("folder","root","Folder");provider.addFolder("inner","folder","Inner")
        val task=task("folder").copy(destination=uri("inner").toString())
        assertTrue(runCatching{engine().run(task){} }.isFailure);assertTrue(provider.records.keys.none{it.startsWith("new-")})
    }
    @Test fun reparentedSourceDuringPauseStopsInsteadOfDeletingItsNewLocation()=runBlocking {
        provider.failDeleteSource=true
        assertTrue(runCatching{engine().run(task(move=true)){} }.isFailure)
        provider.addFolder("outside","root","Outside")
        provider.records["source"]=provider.records.getValue("source").copy(parent="outside")
        provider.failDeleteSource=false
        assertTrue(runCatching{engine().run(TransferJournal(context).read()!!){} }.isFailure)
        assertTrue(provider.records.containsKey("source"))
        assertEquals("outside",provider.records.getValue("source").parent)
    }

}
