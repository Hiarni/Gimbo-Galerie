package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest


data class DocumentEntry(val uri: Uri, val name: String, val mime: String, val flags: Int) {
    val directory get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    val renameable get() = flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
    val deletable get() = flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE != 0
    val canCreate get() = flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
}
fun documentName(value: String): String = value.trim().also {
    requireMessage(it.isNotEmpty() && it.length <= 120 && it !in listOf(".", "..") && it.none { c -> c.isISOControl() || c == '/' || c == '\\' }, R.string.invalid_file_name)
}

class DocumentTools(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    suspend fun describe(uri: Uri): DocumentEntry = requireNotNull(find(uri))
    internal suspend fun find(uri: Uri): DocumentEntry? = withContext(Dispatchers.IO) {
        val query = try { resolver.query(uri, COLUMNS, android.os.Bundle(), null) } catch (_: java.io.FileNotFoundException) { return@withContext null }
        query?.use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            DocumentEntry(uri, cursor.getString(1), cursor.getString(2), cursor.getInt(3))
        } ?: throw java.io.IOException()
    }
    suspend fun children(parent: Uri): List<DocumentEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DocumentEntry>()
        val query = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        resolver.query(query, COLUMNS, android.os.Bundle(), null)?.use { cursor ->
            while (cursor.moveToNext()) {
                currentCoroutineContext().ensureActive()
                requireMessage(result.size < 5000, R.string.folder_too_large)
                result += DocumentEntry(DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(0)), cursor.getString(1), cursor.getString(2), cursor.getInt(3))
            }
        } ?: throw java.io.IOException()
        result.sortedWith(compareBy<DocumentEntry> { !it.directory }.thenBy { it.name.lowercase(java.util.Locale.ROOT) })
    }
    suspend fun createFolder(parent: Uri, value: String) = withContext(Dispatchers.IO) {
        val name = documentName(value)
        val entries = children(parent)
        requireMessage(entries.size < 5000, R.string.folder_too_large)
        requireMessage(entries.none { it.name.equals(name, true) }, R.string.file_name_exists)
        requireNotNull(DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name))
    }
    suspend fun rename(parent: Uri, source: DocumentEntry, value: String) = withContext(Dispatchers.IO) {
        val name = documentName(value)
        requireMessage(source.renameable, R.string.provider_action_unsupported)
        requireMessage(children(parent).none { it.uri != source.uri && it.name.equals(name, true) }, R.string.file_name_exists)
        requireNotNull(DocumentsContract.renameDocument(resolver, source.uri, name))
    }
    suspend fun delete(parent:Uri,source:DocumentEntry)=withContext(Dispatchers.IO) {
        requireMessage(source.deletable && children(parent).any {it.uri==source.uri && it.name==source.name && it.mime==source.mime},R.string.source_review_changed)
        requireMessage(DocumentsContract.deleteDocument(resolver,source.uri),R.string.file_action_failed)
    }
    suspend fun transfer(source: DocumentEntry, target: Uri, move: Boolean,
        progress: suspend (DocumentTransferProgress) -> Unit = {}): Uri = withContext(Dispatchers.IO) {
        if (source.directory) return@withContext DocumentTreeTransfer(resolver, this@DocumentTools).transfer(source, target, move, progress)
        requireMessage(!source.directory && (!move || source.deletable), R.string.provider_action_unsupported)
        val copy = copyVerified(source, target)
        if (move) {
            requireMessage(fingerprint(source.uri) == copy.fingerprint, R.string.copy_source_changed)
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                val removed = runCatching { DocumentsContract.deleteDocument(resolver, source.uri) }.getOrDefault(false)
                requireMessage(removed, R.string.copy_source_kept)
            }
        }
        copy.uri
    }

    internal data class VerifiedCopy(val uri: Uri, val fingerprint: Fingerprint)
    internal data class Fingerprint(val size: Long, val sha256: String)
    internal suspend fun fingerprint(uri: Uri): Fingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count); size += count
            }
        } ?: throw java.io.IOException()
        return Fingerprint(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    internal suspend fun copyVerified(source: DocumentEntry, target: Uri): VerifiedCopy {
        val parent = describe(target)
        requireMessage(parent.directory && parent.canCreate && source.uri != target, R.string.provider_action_unsupported)
        val entries = children(target)
        requireMessage(entries.size < 5000, R.string.folder_too_large)
        val existing = entries.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet()
        val name = freeName(source.name, existing)
        val created = requireNotNull(DocumentsContract.createDocument(resolver, target, source.mime, name))
        var verified = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            resolver.openInputStream(source.uri)?.use { input ->
                resolver.openOutputStream(created, "wt")?.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count); digest.update(buffer, 0, count); size += count
                    }
                } ?: throw java.io.IOException()
            } ?: throw java.io.IOException()
            val expected = Fingerprint(size, digest.digest().joinToString("") { "%02x".format(it) })
            requireMessage(expected == fingerprint(created), R.string.copy_verification_failed)
            currentCoroutineContext().ensureActive()
            verified = true
            return VerifiedCopy(created, expected)
        } finally {
            if (!verified) withContext(NonCancellable + Dispatchers.IO) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
            }
        }
    }
    companion object {
        private val COLUMNS = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_FLAGS)
        fun freeName(original: String, existing: Set<String>): String {
            val name = documentName(original)
            if (name.lowercase(java.util.Locale.ROOT) !in existing) return name
            val dot = name.lastIndexOf('.').takeIf { it > 0 && name.length - it <= 20 } ?: name.length
            val base = name.take(dot); val extension = name.substring(dot)
            for (i in 2..10_000) {
                val candidate = base.take((120 - extension.length - " ($i)".length).coerceAtLeast(1)) + " ($i)" + extension
                if (candidate.lowercase(java.util.Locale.ROOT) !in existing) return candidate
            }
            throw IllegalStateException()
        }
    }
}
