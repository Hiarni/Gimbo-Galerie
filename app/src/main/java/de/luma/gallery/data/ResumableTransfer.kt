package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import kotlinx.coroutines.*
import java.util.Locale


internal class ResumableTransfer(private val context: Context, private val checkpoint: suspend (TransferTask) -> Unit) {
    private val tools = DocumentTools(context)
    private val resolver = context.contentResolver
    suspend fun run(task: TransferTask, progress: suspend (DocumentTransferProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (task.sourceParent == null) {
            val sourceUri = Uri.parse(task.source)
            val root = tools.root(sourceUri)
            if (DocumentsContract.getDocumentId(root) != DocumentsContract.getDocumentId(sourceUri)) {
                suspend fun locate(parent: Uri, depth: Int, seen: MutableSet<String>): String? {
                    requireMessage(depth <= 32 && seen.size < 5000 && seen.add(DocumentsContract.getDocumentId(parent)), R.string.folder_transfer_too_large)
                    val children = tools.children(parent)
                    if (children.any { DocumentsContract.getDocumentId(it.uri) == DocumentsContract.getDocumentId(sourceUri) }) return parent.toString()
                    for (child in children.filter { it.directory }) locate(child.uri, depth+1, seen)?.let { return it }
                    return null
                }
                task.sourceParent = locate(root,0,mutableSetOf())
                requireMessage(task.sourceParent != null, R.string.copy_source_changed)
            }
            requireMessage(!task.move || task.sourceParent != null, R.string.provider_action_unsupported)
            checkpoint(task)
        }
        if (task.nodes.isEmpty()) {
            val seen = mutableSetOf<String>()
            suspend fun visit(entry: DocumentEntry, parent: Int, depth: Int) {
                ensureActive(); requireMessage(depth <= 32 && task.nodes.size < 5000, R.string.folder_transfer_too_large)
                requireMessage(seen.add(DocumentsContract.getDocumentId(entry.uri)), R.string.folder_transfer_unsupported)
                documentName(entry.name)
                requireMessage(entry.flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT == 0 && (!task.move || entry.deletable), R.string.provider_action_unsupported)
                val index = task.nodes.size
                task.nodes += TransferNode(entry.uri.toString(), parent, entry.name, entry.mime, entry.flags)
                if (entry.directory) for (child in tools.children(entry.uri)) visit(child, index, depth + 1)
            }
            progress(DocumentTransferProgress(DocumentTransferPhase.SCANNING, 0, 0, ""))
            visit(tools.describe(Uri.parse(task.source)), -1, 0)
            requireMessage(Uri.parse(task.source).authority != Uri.parse(task.destination).authority || task.nodes.none {
                DocumentsContract.getDocumentId(Uri.parse(it.source)) == DocumentsContract.getDocumentId(Uri.parse(task.destination))
            }, R.string.folder_target_inside_source)
            checkpoint(task)
        }
        val parent = tools.describe(Uri.parse(task.destination))
        requireMessage(parent.directory && parent.canCreate, R.string.provider_action_unsupported)
        for ((index, node) in task.nodes.withIndex()) {
            ensureActive()
            progress(DocumentTransferProgress(DocumentTransferPhase.COPYING, index, task.nodes.size, node.name))
            val destination = if (node.parent < 0) Uri.parse(task.destination) else Uri.parse(requireNotNull(task.nodes[node.parent].target))
            if (node.target == null) {
                requireMessage(!task.verified, R.string.copy_verification_failed)
                val existing = tools.children(destination)
                requireMessage(existing.size < 5000, R.string.folder_too_large)
                val name = DocumentTools.freeName(node.name, existing.map { it.name.lowercase(Locale.ROOT) }.toSet())

                val created = requireNotNull(DocumentsContract.createDocument(resolver, destination, node.mime, name))
                node.target = created.toString(); node.targetName = tools.describe(created).name
                withContext(NonCancellable) { checkpoint(task) }
            }
            val target = Uri.parse(node.target)
            val current = tools.describe(target)
            requireMessage(current.mime == node.mime && current.name == node.targetName && tools.children(destination).any { it.uri == target }, R.string.copy_verification_failed)
            if (!node.directory && node.hash == null) {
                requireMessage(!task.verified && !node.deleting, R.string.copy_verification_failed)
                checkSource(task, node)

                resolver.openInputStream(Uri.parse(node.source))?.use { input ->
                    resolver.openOutputStream(target, "wt")?.use { output ->
                        val buffer = ByteArray(65536)
                        while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                    } ?: throw java.io.IOException()
                } ?: throw java.io.IOException()
                val expected = tools.fingerprint(Uri.parse(node.source))
                requireMessage(tools.fingerprint(target) == expected, R.string.copy_verification_failed)
                node.size = expected.size; node.hash = expected.sha256
                withContext(NonCancellable) { checkpoint(task) }
            }
        }
        for ((index, node) in task.nodes.withIndex()) {
            progress(DocumentTransferProgress(DocumentTransferPhase.VERIFYING, index, task.nodes.size, node.name))
            verifyTarget(node)
            if (!node.removed) {
                if (!exists(task, index)) {
                    requireMessage(node.deleting && task.verified, R.string.copy_source_changed)


                    val reparented = runCatching { tools.find(Uri.parse(node.source)) }.getOrNull()
                    requireMessage(reparented == null, R.string.folder_source_changed)
                    node.removed = true; checkpoint(task)
                } else { checkSource(task, node); if (!node.directory) verifySource(node) }
            }
        }

        for ((index, node) in task.nodes.withIndex()) if (node.directory && !node.removed) {
            val expected = task.nodes.filter { it.parent == index && !it.removed }.map { DocumentsContract.getDocumentId(Uri.parse(it.source)) }.toSet()
            requireMessage(tools.children(Uri.parse(node.source)).map { DocumentsContract.getDocumentId(it.uri) }.toSet() == expected, R.string.folder_source_changed)
        }
        task.verified = true; checkpoint(task)
        if (task.move) for ((index, node) in task.nodes.withIndex().toList().asReversed()) {
            if (node.removed) continue
            progress(DocumentTransferProgress(DocumentTransferPhase.REMOVING, task.nodes.count { it.removed }, task.nodes.size, node.name))
            verifyTarget(node); checkSource(task, node)
            if (node.directory) requireMessage(tools.children(Uri.parse(node.source)).isEmpty(), R.string.folder_source_changed)
            else verifySource(node)
            ensureActive()
            withContext(NonCancellable) {
                node.deleting = true; checkpoint(task)
                requireMessage(DocumentsContract.deleteDocument(resolver, Uri.parse(node.source)), R.string.folder_delete_failed)
                node.removed = true; checkpoint(task)
            }
        }
    }
    private suspend fun exists(task: TransferTask, index: Int): Boolean {
        val node = task.nodes[index]
        val parent = if (node.parent >= 0) {
            if (!exists(task, node.parent)) return false
            task.nodes[node.parent].source
        } else task.sourceParent
        return if (parent == null) tools.find(Uri.parse(node.source)) != null else tools.children(Uri.parse(parent)).any {
            DocumentsContract.getDocumentId(it.uri) == DocumentsContract.getDocumentId(Uri.parse(node.source))
        }
    }
    private suspend fun verifyTarget(node: TransferNode) {
        val target = tools.describe(Uri.parse(requireNotNull(node.target)))
        requireMessage(target.name == node.targetName && target.mime == node.mime, R.string.copy_verification_failed)
        if (!node.directory) requireMessage(tools.fingerprint(target.uri) == DocumentTools.Fingerprint(node.size, requireNotNull(node.hash)), R.string.copy_verification_failed)
    }
    private suspend fun verifySource(node: TransferNode) {
        requireMessage(tools.fingerprint(Uri.parse(node.source)) == DocumentTools.Fingerprint(node.size, requireNotNull(node.hash)), R.string.copy_source_changed)
    }
    private suspend fun checkSource(task: TransferTask, node: TransferNode) {
        val current = tools.describe(Uri.parse(node.source))
        requireMessage(current.name == node.name && current.mime == node.mime && (!task.move || current.deletable), R.string.copy_source_changed)
        val parent = if (node.parent >= 0) task.nodes[node.parent].source else task.sourceParent
        if (parent != null) requireMessage(tools.children(Uri.parse(parent)).any {
            DocumentsContract.getDocumentId(it.uri) == DocumentsContract.getDocumentId(current.uri)
        }, R.string.folder_source_changed)
    }
}
