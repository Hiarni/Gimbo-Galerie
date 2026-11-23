package de.luma.gallery.model

import de.luma.gallery.R

enum class DuplicateKind(val labelRes:Int) { EXACT(R.string.duplicates_exact), SIMILAR(R.string.duplicates_similar) }
data class DuplicateFile(val media:GalleryMedia,val sha256:String,val bytes:Long)
data class DuplicateGroup(val id:String,val kind:DuplicateKind,val files:List<DuplicateFile>) {
    init {require(files.size>=2 && files.map {it.media.id}.distinct().size==files.size)}
}
data class DuplicateReport(val groups:List<DuplicateGroup>,val checked:Int,val unavailable:Int) {
    init {require(groups.flatMap {it.files}.map {it.media.id}.distinct().size==groups.sumOf {it.files.size})}
    fun safeSelection(ids:Set<String>):List<DuplicateFile> {
        require(ids.isNotEmpty() && ids.size<=MEDIA_ACTION_LIMIT)
        val all=groups.flatMap {it.files};require(ids.all {id->all.any {it.media.id==id}})
        require(groups.all {group->group.files.any {it.media.id !in ids}})
        return all.filter {it.media.id in ids}
    }
    fun exactExtras():Set<String> = groups.filter {it.kind==DuplicateKind.EXACT}.flatMap {group->
        group.files.sortedWith(compareByDescending<DuplicateFile> {it.bytes}.thenBy {it.media.timestamp}.thenBy {it.media.id}).drop(1).map {it.media.id}
    }.take(MEDIA_ACTION_LIMIT).toSet()
}
