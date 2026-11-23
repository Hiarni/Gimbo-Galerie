package de.luma.gallery.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


internal data class TransferNode(val source: String, val parent: Int, val name: String, val mime: String,
    val flags: Int, var target: String? = null, var targetName: String? = null,
    var size: Long = -1, var hash: String? = null, var deleting: Boolean = false, var removed: Boolean = false) {
    val directory get() = mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR
    fun entry() = DocumentEntry(Uri.parse(source), name, mime, flags)
}
internal data class TransferTask(val source: String, val destination: String, val move: Boolean,
    val nodes: MutableList<TransferNode> = mutableListOf(), var verified: Boolean = false, var sourceParent: String? = null) {
    fun json(): String = JSONObject().put("version", 1).put("source", source).put("destination", destination)
        .put("move", move).put("sourceParent", sourceParent).put("verified", verified).put("nodes", JSONArray(nodes.map { n ->
            JSONObject().put("source", n.source).put("parent", n.parent).put("name", n.name).put("mime", n.mime)
                .put("flags", n.flags).put("target", n.target).put("targetName", n.targetName).put("size", n.size)
                .put("hash", n.hash).put("deleting", n.deleting).put("removed", n.removed)
        })).toString()
    companion object {
        fun read(value: String): TransferTask {
            require(value.toByteArray().size <= 8 * 1024 * 1024)
            val j = JSONObject(value); require(j.getInt("version") == 1)
            fun uri(value: String) = value.also { require(it.length <= 8192 && Uri.parse(it).scheme == "content") }
            val a = j.getJSONArray("nodes"); require(a.length() <= 5000)
            val task = TransferTask(uri(j.getString("source")), uri(j.getString("destination")), j.getBoolean("move"), verified = j.getBoolean("verified"), sourceParent = if (j.isNull("sourceParent")) null else uri(j.getString("sourceParent")))
            for (i in 0 until a.length()) {
                val n = a.getJSONObject(i); val parent = n.getInt("parent"); require(parent >= -1 && parent < i && (i == 0) == (parent == -1))
                val node = TransferNode(uri(n.getString("source")), parent, documentName(n.getString("name")), n.getString("mime"), n.getInt("flags"),
                    if (n.isNull("target")) null else uri(n.getString("target")), if (n.isNull("targetName")) null else documentName(n.getString("targetName")),
                    n.getLong("size"), if (n.isNull("hash")) null else n.getString("hash"), n.getBoolean("deleting"), n.getBoolean("removed"))
                require(node.mime.length in 1..256 && node.size >= -1 && (node.hash == null || node.hash!!.matches(Regex("[a-f0-9]{64}"))))
                require(!node.removed || node.deleting); require(!node.deleting || task.verified && task.move)
                if (parent >= 0) require(task.nodes[parent].directory)
                task.nodes += node
            }
            require(task.nodes.map { it.source }.distinct().size == task.nodes.size)
            return task
        }
    }
}
internal class TransferJournal(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "luma-transfer.json"))
    fun write(task: TransferTask) {
        val data = task.json().toByteArray(); require(data.size <= 8 * 1024 * 1024)
        val stream = file.startWrite()
        try { stream.write(data); file.finishWrite(stream) } catch (e: Throwable) { file.failWrite(stream); throw e }
    }
    fun read(): TransferTask? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return file.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 8 * 1024 * 1024); output.write(buffer, 0, count) }
            val bytes = output.toByteArray()
            require(bytes.size <= 8 * 1024 * 1024); TransferTask.read(bytes.toString(Charsets.UTF_8))
        }
    }
    fun delete() = file.delete()
}
