package de.luma.gallery.data

import android.Manifest
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import de.luma.gallery.R
import de.luma.gallery.localization.ResourceFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DocumentTreeTransferTest {
    private lateinit var provider: DocumentToolsTest.TestProvider
    private lateinit var tools: DocumentTools
    private val tree get() = DocumentsContract.buildTreeDocumentUri("luma.test.docs", "root")
    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        provider = DocumentToolsTest.TestProvider().apply { attachInfo(context, ProviderInfo().apply {
            authority = "luma.test.docs"; exported = true; grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS; writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }) }
        ShadowContentResolver.registerProviderInternal("luma.test.docs", provider)
        tools = DocumentTools(context)
        provider.addFolder("album", "root", "Album")
        provider.addFolder("inner", "album", "Nested")
        provider.addFolder("empty", "album", "Empty")
        provider.add("first", "album", "A.jpg", byteArrayOf(1, 2, 3))
        provider.add("second", "inner", "B.jpg", byteArrayOf(4, 5, 6))
        provider.add("zero", "inner", "Zero.jpg", byteArrayOf())
    }
    @After fun cleanup() { provider.folder.deleteRecursively() }
    private suspend fun transfer(move: Boolean = false, progress: suspend (DocumentTransferProgress) -> Unit = {}) =
        tools.transfer(tools.describe(uri("album")), uri("target"), move, progress)

    private suspend fun contents(root: Uri): Map<String, List<Byte>> {
        val result = linkedMapOf<String, List<Byte>>()
        suspend fun visit(current: Uri, prefix: String) {
            for (entry in tools.children(current)) {
                val path = prefix + entry.name
                if (entry.directory) { result["$path/"] = emptyList(); visit(entry.uri, "$path/") }
                else result[path] = provider.records.getValue(DocumentsContract.getDocumentId(entry.uri)).file!!.readBytes().toList()
            }
        }
        visit(root, "")
        return result
    }
    private fun assertFailure(expected: Int, error: Throwable?) {
        assertNotNull(error)
        assertEquals(expected, (error as ResourceFailure).messageRes)
    }

    @Test fun nestedCopyPreservesBytesEmptyFilesAndFoldersAndNeverMergesExistingFolder() = runBlocking {
        val original = contents(uri("album"))
        provider.addFolder("existing", "target", "Album")
        provider.add("keep", "existing", "Keep.jpg", byteArrayOf(42))
        val copy = transfer()
        assertEquals("Album (2)", tools.describe(copy).name)
        assertEquals(original, contents(copy))
        assertEquals(original, contents(uri("album")))
        assertEquals(mapOf("Keep.jpg" to listOf(42.toByte())), contents(uri("existing")))
        assertTrue(provider.deletions.isEmpty())
    }

    @Test fun moveCopiesEveryEntryBeforeFirstDeletionAndOnlyRemovesEmptySourceDirectories() = runBlocking {
        val original = contents(uri("album"))
        var firstRemoval = true
        val phases = mutableSetOf<DocumentTransferPhase>()
        val copy = transfer(true) { next ->
            phases += next.phase
            if (firstRemoval && next.phase == DocumentTransferPhase.REMOVING) {
                assertEquals(original, contents(uri("album")))
                assertEquals(original, contents(tools.children(uri("target")).single().uri))
                assertTrue(provider.deletions.isEmpty())
                firstRemoval = false
            }
        }
        assertFalse(provider.records.containsKey("album"))
        assertEquals(original, contents(copy))
        assertEquals(DocumentTransferPhase.entries.toSet(), phases)
        assertEquals(setOf("album", "inner", "empty", "first", "second", "zero"), provider.deletions.toSet())
    }

    @Test fun corruptCopyDetectedInFinalPassStopsAllSourceRemoval() = runBlocking {
        val failure = runCatching { transfer(true) { next ->
            if (next.phase == DocumentTransferPhase.VERIFYING && next.completed == 0) {
                provider.records.filterKeys { it.startsWith("new-") }.values.first { it.file != null }.file!!.writeBytes(byteArrayOf(99))
            }
        } }.exceptionOrNull()
        assertFailure(R.string.copy_verification_failed, failure)
        assertTrue(provider.records.containsKey("first"))
        assertTrue(provider.records.containsKey("second"))
        assertFalse(provider.deletions.any { !it.startsWith("new-") })
    }

    @Test fun sourceChangedAfterCopyIsRetainedWithCompletedCopies() = runBlocking {
        val failure = runCatching { transfer(true) { next ->
            if (next.phase == DocumentTransferPhase.VERIFYING && next.completed == 0) provider.records.getValue("second").file!!.writeBytes(byteArrayOf(7, 8, 9))
        } }.exceptionOrNull()
        assertFailure(R.string.copy_source_changed, failure)
        assertArrayEquals(byteArrayOf(7, 8, 9), provider.records.getValue("second").file!!.readBytes())
        val copy = tools.children(uri("target")).single().uri
        assertEquals(listOf(4.toByte(), 5.toByte(), 6.toByte()), contents(copy)["Nested/B.jpg"])
        assertTrue(provider.records.containsKey("first"))
    }

    @Test fun newSourceChildDuringCopyStopsBeforeDeletingAnyOriginals() = runBlocking {
        val failure = runCatching { transfer(true) { next ->
            if (next.phase == DocumentTransferPhase.VERIFYING && next.completed == 0) provider.add("late", "album", "Late.jpg", byteArrayOf(10))
        } }.exceptionOrNull()
        assertFailure(R.string.folder_source_changed, failure)
        assertTrue(provider.records.containsKey("late"))
        assertTrue(provider.records.containsKey("first"))
        assertTrue(provider.records.containsKey("second"))
        assertFalse(provider.deletions.any { !it.startsWith("new-") })
    }

    @Test fun destinationInsideSourceIsRejectedEvenWithADifferentTreeGrant() = runBlocking {
        val granted = DocumentsContract.buildTreeDocumentUri("luma.test.docs", "inner")
        val nested = tools.root(granted)
        for (target in listOf(uri("album"), nested)) {
            assertFailure(R.string.folder_target_inside_source, runCatching { tools.transfer(tools.describe(uri("album")), target, true) }.exceptionOrNull())
        }
        assertTrue(provider.records.keys.none { it.startsWith("new-") })
    }

    @Test fun cancellationWhileCopyingKeepsVerifiedCopiesAndAllOriginals() = runBlocking {
        val original = contents(uri("album"))
        val failure = runCatching { transfer(true) { next ->
            if (next.phase == DocumentTransferPhase.COPYING && next.name == "Zero.jpg") throw CancellationException()
        } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(original, contents(uri("album")))
        val copy = tools.children(uri("target")).single().uri
        assertEquals(listOf(4.toByte(), 5.toByte(), 6.toByte()), contents(copy)["Nested/B.jpg"])
        assertFalse(provider.deletions.any { !it.startsWith("new-") })
    }

    @Test fun cancellationDuringRemovalKeepsFullCopyAndUndeletedSources() = runBlocking {
        val original = contents(uri("album"))
        val failure = runCatching { transfer(true) { next ->
            if (next.phase == DocumentTransferPhase.REMOVING && next.completed == 1) throw CancellationException()
        } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(original, contents(tools.children(uri("target")).single().uri))
        assertEquals(1, provider.deletions.size)
        assertTrue(provider.records.containsKey("album"))
        assertTrue(provider.records.containsKey("first"))
    }

    @Test fun failedDeletionLeavesFullVerifiedCopyAndRemainingOriginals() = runBlocking {
        val original = contents(uri("album"))
        provider.blockedDeletes += "zero"
        assertFailure(R.string.folder_delete_failed, runCatching { transfer(true) }.exceptionOrNull())
        assertEquals(original, contents(tools.children(uri("target")).single().uri))
        assertTrue(provider.records.containsKey("zero"))
        assertTrue(provider.records.containsKey("album"))
        assertTrue(provider.records.containsKey("first"))
    }

    @Test fun newChildDuringRemovalIsNeverDeletedAsPartOfSourceFolder() = runBlocking {
        val original = contents(uri("album"))
        provider.afterDelete = { id -> if (id == "second") provider.add("late", "inner", "Late.jpg", byteArrayOf(11)) }
        assertFailure(R.string.folder_source_changed, runCatching { transfer(true) }.exceptionOrNull())
        assertEquals(original, contents(tools.children(uri("target")).single().uri))
        assertTrue(provider.records.containsKey("late"))
        assertTrue(provider.records.containsKey("inner"))
        assertTrue(provider.records.containsKey("album"))
    }

    @Test fun unsupportedDeletionInDescendantIsRejectedBeforeWritingAnything() = runBlocking {
        provider.flagOverrides["zero"] = 0
        assertFailure(R.string.provider_action_unsupported, runCatching { transfer(true) }.exceptionOrNull())
        assertTrue(tools.children(uri("target")).isEmpty())
        assertNotNull(transfer(false))
    }

    @Test fun repeatedProviderDocumentReferencesAreRejectedBeforeCreatingDestination() = runBlocking {
        provider.repeatedChild = "inner" to "album"
        assertFailure(R.string.folder_transfer_unsupported, runCatching { transfer() }.exceptionOrNull())
        assertTrue(tools.children(uri("target")).isEmpty())
    }

    @Test fun overlyDeepFolderTreeIsRejectedBeforeCreatingDestination() = runBlocking {
        var parent = "inner"
        for (index in 1..32) { val id = "depth-$index"; provider.addFolder(id, parent, id); parent = id }
        assertFailure(R.string.folder_transfer_too_large, runCatching { transfer() }.exceptionOrNull())
        assertTrue(tools.children(uri("target")).isEmpty())
        assertTrue(provider.deletions.isEmpty())
    }

    @Test fun excessiveTotalEntryCountIsRejectedBeforeCreatingDestination() = runBlocking {

        for (index in 1..4995) provider.addFolder("many-$index", "album", "Item $index")
        assertFailure(R.string.folder_transfer_too_large, runCatching { transfer() }.exceptionOrNull())
        assertTrue(tools.children(uri("target")).isEmpty())
        assertTrue(provider.deletions.isEmpty())
    }
}
