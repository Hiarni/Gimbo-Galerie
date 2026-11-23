package de.luma.gallery.data

import android.Manifest
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import de.luma.gallery.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class DocumentToolsTest {
    private lateinit var provider: TestProvider
    private lateinit var tools: DocumentTools
    private val tree get() = DocumentsContract.buildTreeDocumentUri("luma.test.docs", "root")
    private val target get() = DocumentsContract.buildDocumentUriUsingTree(tree, "target")
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        provider = TestProvider().apply { attachInfo(context, ProviderInfo().apply {
            authority = "luma.test.docs"; exported = true; grantUriPermissions = true
            readPermission = Manifest.permission.MANAGE_DOCUMENTS; writePermission = Manifest.permission.MANAGE_DOCUMENTS
        }) }
        ShadowContentResolver.registerProviderInternal("luma.test.docs", provider)
        tools = DocumentTools(context)
    }
    @After fun cleanup() { provider.folder.deleteRecursively() }
    private suspend fun source() = tools.children(tools.root(tree)).first { !it.directory }

    @Test fun copyUsesFreeNameAndVerifiesWithoutOverwritingOriginalOrExistingFile() = runBlocking {
        val item = source(); val original = provider.records.getValue("source").file!!.readBytes()
        provider.add("existing", "target", "Photo.jpg", byteArrayOf(42))
        val copied = tools.transfer(item, target, false)
        assertEquals("Photo (2).jpg", tools.describe(copied).name)
        assertArrayEquals(original, provider.records.getValue("source").file!!.readBytes())
        assertArrayEquals(original, provider.records.getValue(DocumentsContract.getDocumentId(copied)).file!!.readBytes())
        assertArrayEquals(byteArrayOf(42), provider.records.getValue("existing").file!!.readBytes())
    }
    @Test fun moveDeletesOriginalOnlyAfterVerificationAndFailedVerificationCleansTarget() = runBlocking {
        val item = source()
        provider.corruptNewReads = true
        assertTrue(runCatching { tools.transfer(item, target, true) }.isFailure)
        assertTrue(provider.records.containsKey("source"))
        assertTrue(tools.children(target).isEmpty())
        provider.corruptNewReads = false
        val copied = tools.transfer(item, target, true)
        assertFalse(provider.records.containsKey("source"))
        assertNotNull(tools.describe(copied))
    }
    @Test fun failedSourceDeletionKeepsVerifiedCopyAndFolderActionsRespectCollisions() = runBlocking {
        provider.failDeleteSource = true
        assertTrue(runCatching { tools.transfer(source(), target, true) }.isFailure)
        assertTrue(provider.records.containsKey("source"))
        assertEquals(1, tools.children(target).size)
        val folder = tools.createFolder(target, "New")
        tools.rename(target, tools.describe(folder), "Renamed")
        assertEquals("Renamed", tools.describe(folder).name)
        assertTrue(runCatching { tools.createFolder(target, "renamed") }.isFailure)
        assertTrue(runCatching { documentName("../escape") }.isFailure)
    }

    @Test fun sourceChangedDuringVerificationIsKeptAlongsideTheVerifiedOldCopy() = runBlocking {
        val item = source()
        val original = provider.records.getValue("source").file!!.readBytes()
        val changed = ByteArray(original.size) { 127 }
        provider.changedSourceOnVerification = changed
        val failure = runCatching { tools.transfer(item, target, true) }.exceptionOrNull()
        assertEquals(de.luma.gallery.R.string.copy_source_changed, (failure as de.luma.gallery.localization.ResourceFailure).messageRes)
        assertArrayEquals(changed, provider.records.getValue("source").file!!.readBytes())
        val kept = tools.children(target).single()
        assertArrayEquals(original, provider.records.getValue(DocumentsContract.getDocumentId(kept.uri)).file!!.readBytes())
    }

    class TestProvider : DocumentsProvider() {
        data class Record(var name: String, val mime: String, val parent: String?, val file: File?)
        val folder = File(RuntimeEnvironment.getApplication().cacheDir, "docs-${UUID.randomUUID()}").apply { mkdirs() }
        val records = linkedMapOf("root" to Record("Root", DocumentsContract.Document.MIME_TYPE_DIR, null, null),
            "target" to Record("Target", DocumentsContract.Document.MIME_TYPE_DIR, "root", null))
        var onOpenDocument: (() -> Unit)? = null
        var changedSourceOnVerification: ByteArray? = null
        var corruptNewReads = false
        var failDeleteSource = false
        val blockedDeletes = mutableSetOf<String>()
        val flagOverrides = mutableMapOf<String, Int>()
        val deletions = mutableListOf<String>()
        var afterDelete: ((String) -> Unit)? = null
        var repeatedChild: Pair<String, String>? = null
        init { add("source", "root", "Photo.jpg", ByteArray(200_000) { (it % 251).toByte() }) }
        fun add(id: String, parent: String, name: String, bytes: ByteArray) {
            records[id] = Record(name, "image/jpeg", parent, File(folder, id).apply { writeBytes(bytes) })
        }
        fun addFolder(id: String, parent: String, name: String) {
            records[id] = Record(name, DocumentsContract.Document.MIME_TYPE_DIR, parent, null)
        }
        override fun onCreate() = true
        override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        private fun cursor(ids: List<String>, projection: Array<out String>?): Cursor {
            val columns = projection ?: arrayOf("document_id", "_display_name", "mime_type", "flags")
            val cursor = MatrixCursor(columns)
            for (id in ids) {
                val record = records[id] ?: throw java.io.FileNotFoundException()
                cursor.addRow(columns.map<String, Any?> { column -> when (column) {
                    "document_id" -> id; "_display_name" -> record.name; "mime_type" -> record.mime
                    "flags" -> flagOverrides[id] ?: (DocumentsContract.Document.FLAG_SUPPORTS_RENAME or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or
                        (if (record.file == null) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0))
                    else -> null
                } }.toTypedArray())
            }
            return cursor
        }
        override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(listOf(documentId), projection)
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
            val ids = records.filterValues { it.parent == parentDocumentId }.keys.toMutableList()
            repeatedChild?.takeIf { it.first == parentDocumentId }?.let { ids += it.second }
            return cursor(ids, projection)
        }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            onOpenDocument?.invoke()
            val file = records.getValue(documentId).file ?: throw java.io.FileNotFoundException()
            if (mode == "r" && documentId.startsWith("new-")) changedSourceOnVerification?.let {
                records.getValue("source").file!!.writeBytes(it); changedSourceOnVerification = null
            }
            if (corruptNewReads && mode == "r" && documentId.startsWith("new-")) file.writeBytes(byteArrayOf(9))
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val id = "new-${UUID.randomUUID()}"
            records[id] = Record(displayName, mimeType, parentDocumentId,
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) null else File(folder, id).apply { createNewFile() })
            return id
        }
        override fun renameDocument(documentId: String, displayName: String): String { records.getValue(documentId).name = displayName; return documentId }
        override fun deleteDocument(documentId: String) {
            if (documentId in blockedDeletes || (documentId == "source" && failDeleteSource)) throw java.io.FileNotFoundException()

            check(records.values.none { it.parent == documentId }) { "Attempt to delete a populated folder" }
            deletions += documentId
            records.remove(documentId)?.file?.delete()
            afterDelete?.invoke(documentId)
        }
        override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
            var current: String? = documentId
            val seen = mutableSetOf<String>()
            while (current != null && seen.add(current)) {
                if (current == parentDocumentId) return true
                current = records[current]?.parent
            }
            return false
        }
    }
}
