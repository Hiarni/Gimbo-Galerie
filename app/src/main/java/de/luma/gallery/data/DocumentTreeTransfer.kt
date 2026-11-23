package de.luma.gallery.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

enum class DocumentTransferPhase { SCANNING, COPYING, VERIFYING, REMOVING }
data class DocumentTransferProgress(val phase: DocumentTransferPhase, val completed: Int, val total: Int, val name: String)


internal class DocumentTreeTransfer(private val resolver: ContentResolver, private val tools: DocumentTools) {
    private data class Node(val entry: DocumentEntry, val parentId: String?) {
        val id get() = DocumentsContract.getDocumentId(entry.uri)
    }
    private data class Signature(val name: String, val mime: String, val parentId: String?)
    private data class Copied(val node: Node, val copy: DocumentTools.VerifiedCopy)

    suspend fun transfer(source: DocumentEntry, target: Uri, move: Boolean,
        progress: suspend (DocumentTransferProgress) -> Unit): Uri {
        progress(DocumentTransferProgress(DocumentTransferPhase.SCANNING, 0, 0, source.name))
        val nodes = snapshot(source.uri)
        requireMessage(nodes.first().entry.name == source.name && nodes.first().entry.mime == source.mime, R.string.copy_source_changed)
        requireMessage(!move || nodes.all { it.entry.deletable }, R.string.provider_action_unsupported)
        val parent = tools.describe(target)
        requireMessage(parent.directory && parent.canCreate, R.string.provider_action_unsupported)

        requireMessage(source.uri.authority != target.authority || nodes.none { it.id == DocumentsContract.getDocumentId(target) }, R.string.folder_target_inside_source)
        val directories = linkedMapOf<String, Uri>()
        val files = mutableListOf<Copied>()
        var complete = false
        try {
            for ((index, node) in nodes.withIndex()) {
                currentCoroutineContext().ensureActive()
                progress(DocumentTransferProgress(DocumentTransferPhase.COPYING, index, nodes.size, node.entry.name))
                val destination = node.parentId?.let { directories.getValue(it) } ?: target
                if (node.entry.directory) {
                    val existing = tools.children(destination)
                    requireMessage(existing.size < MAX_ENTRIES, R.string.folder_too_large)
                    val name = DocumentTools.freeName(node.entry.name, existing.map { it.name.lowercase(Locale.ROOT) }.toSet())
                    directories[node.id] = tools.createFolder(destination, name)
                } else files += Copied(node, tools.copyVerified(node.entry, destination))
            }

            for ((index, copied) in files.withIndex()) {
                progress(DocumentTransferProgress(DocumentTransferPhase.VERIFYING, index, files.size, copied.node.entry.name))
                verifyFile(copied)
            }
            requireMessage(signatures(snapshot(source.uri)) == signatures(nodes), R.string.folder_source_changed)
            for (destination in directories.values) requireMessage(tools.describe(destination).directory, R.string.copy_verification_failed)
            currentCoroutineContext().ensureActive()
            complete = true
            if (move) {
                var removed = 0
                for (copied in files) {
                    progress(DocumentTransferProgress(DocumentTransferPhase.REMOVING, removed, nodes.size, copied.node.entry.name))

                    verifyFile(copied)
                    requireMessage(signature(Node(tools.describe(copied.node.entry.uri), copied.node.parentId)) == signature(copied.node), R.string.folder_source_changed)
                    remove(copied.node.entry.uri)
                    removed++
                }
                for (node in nodes.asReversed().filter { it.entry.directory }) {
                    progress(DocumentTransferProgress(DocumentTransferPhase.REMOVING, removed, nodes.size, node.entry.name))
                    requireMessage(tools.describe(directories.getValue(node.id)).directory, R.string.copy_verification_failed)
                    requireMessage(signature(Node(tools.describe(node.entry.uri), node.parentId)) == signature(node), R.string.folder_source_changed)
                    requireMessage(tools.children(node.entry.uri).isEmpty(), R.string.folder_source_changed)

                    remove(node.entry.uri)
                    removed++
                }
            }
            return directories.getValue(nodes.first().id)
        } finally {
            if (!complete) withContext(NonCancellable + Dispatchers.IO) {

                for (directory in directories.values.toList().asReversed()) runCatching {
                    if (tools.children(directory).isEmpty()) DocumentsContract.deleteDocument(resolver, directory)
                }
            }
        }
    }

    private suspend fun verifyFile(copied: Copied) {
        requireMessage(tools.fingerprint(copied.copy.uri) == copied.copy.fingerprint, R.string.copy_verification_failed)
        requireMessage(tools.fingerprint(copied.node.entry.uri) == copied.copy.fingerprint, R.string.copy_source_changed)
    }

    private suspend fun remove(uri: Uri) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            requireMessage(runCatching { DocumentsContract.deleteDocument(resolver, uri) }.getOrDefault(false), R.string.folder_delete_failed)
        }
    }

    private suspend fun snapshot(root: Uri): List<Node> {
        val result = mutableListOf<Node>()
        val seen = mutableSetOf<String>()
        suspend fun visit(entry: DocumentEntry, parentId: String?, depth: Int) {
            currentCoroutineContext().ensureActive()
            requireMessage(depth <= MAX_DEPTH && result.size < MAX_ENTRIES, R.string.folder_transfer_too_large)
            val node = Node(entry, parentId)
            requireMessage(seen.add(node.id), R.string.folder_transfer_unsupported)
            documentName(entry.name)
            requireMessage(entry.mime.isNotBlank() && entry.flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT == 0, R.string.folder_transfer_unsupported)
            result += node
            if (entry.directory) for (child in tools.children(entry.uri)) visit(child, node.id, depth + 1)
        }
        val entry = tools.describe(root)
        requireMessage(entry.directory, R.string.provider_action_unsupported)
        visit(entry, null, 0)
        return result
    }

    private fun signature(node: Node) = Signature(node.entry.name, node.entry.mime, node.parentId)
    private fun signatures(nodes: List<Node>) = nodes.associate { it.id to signature(it) }

    companion object {
        private const val MAX_ENTRIES = 5000
        private const val MAX_DEPTH = 32
    }
}
