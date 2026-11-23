package de.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.sqrt

object DuplicateScanner {
    internal data class Visual(val hash:Long,val ratio:Float,val pixels:IntArray)
    suspend fun hash(context:Context,media:GalleryMedia):Pair<String,Long> = withContext(Dispatchers.IO) {
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(64*1024);var size=0L
        context.contentResolver.openInputStream(media.uri)?.use {input->
            while(true) {ensureActive();val count=input.read(buffer);if(count<0) break;if(count>0) {digest.update(buffer,0,count);size+=count}}
        } ?: error("Source unavailable")
        require(size>0)
        digest.digest().joinToString("") {"%02x".format(it.toInt() and 255)} to size
    }
    internal interface Checkpoints {
        fun file(media:GalleryMedia):Pair<Boolean,DuplicateFile?>
        fun saveFile(media:GalleryMedia,file:DuplicateFile?)
        fun visual(digest:String):Pair<Boolean,Visual?>
        fun saveVisual(digest:String,visual:Visual?)
    }
    suspend fun scan(context:Context,media:List<GalleryMedia>,similar:Boolean,progress:(Boolean,Int,Int)->Unit):DuplicateReport = scan(context,media,similar,null,progress)
    internal suspend fun scan(context:Context,media:List<GalleryMedia>,similar:Boolean,checkpoints:Checkpoints?,progress:(Boolean,Int,Int)->Unit):DuplicateReport=withContext(Dispatchers.IO) {
        val sources=media.filter {!it.isVideo}.distinctBy {it.uri}
        val files=mutableListOf<DuplicateFile>();var unavailable=0
        sources.forEachIndexed {index,item->
            ensureActive()
            val cached=checkpoints?.file(item)
            val file=if(cached?.first==true) cached.second else {
                val checked=try {val (digest,size)=hash(context,item);DuplicateFile(item,digest,size)}
                catch(e:Exception) {if(e is CancellationException) throw e;null}
                checkpoints?.saveFile(item,checked)
                checked
            }
            if(file!=null) files+=file else unavailable++
            progress(false,index+1,sources.size)
        }
        val families=files.groupBy {it.sha256}.values.sortedByDescending {it.first().bytes}
        val groups=mutableListOf<MutableList<DuplicateFile>>()
        if(similar) {
            val tree=VisualTree()
            families.forEachIndexed {index,family->
                ensureActive()
                val digest=family.first().sha256
                val cached=checkpoints?.visual(digest)
                val visual=if(cached?.first==true) cached.second else {
                    val checked=try {
                        val image=PhotoEngine.decode(context,family.first().media.uri,96)
                        try {fingerprint(image)} finally {image.recycle()}
                    } catch(e:Exception) {if(e is CancellationException) throw e;null}
                    checkpoints?.saveVisual(digest,checked)
                    checked
                }
                if(visual==null) unavailable++
                val match=visual?.let {tree.matches(it.hash,4).firstOrNull {candidate->looksSimilar(it,candidate.visual)}}
                if(match!=null) groups[match.group].addAll(family)
                else {val group=groups.size;groups+=family.toMutableList();if(visual!=null) tree.add(visual,group)}
                progress(true,index+1,families.size)
            }
        } else families.forEach {groups+=it.toMutableList()}
        val result=groups.filter {it.size>=2}.map {group->
            val sorted=group.sortedWith(compareByDescending<DuplicateFile> {it.bytes}.thenBy {it.media.timestamp}.thenBy {it.media.id})
            DuplicateGroup(sorted.first().media.id,if(group.map {it.sha256}.distinct().size==1) DuplicateKind.EXACT else DuplicateKind.SIMILAR,sorted)
        }.sortedWith(compareBy<DuplicateGroup> {it.kind}.thenByDescending {it.files.size})
        DuplicateReport(result,files.size,unavailable)
    }
    suspend fun verify(context:Context,report:DuplicateReport,ids:Set<String>):List<GalleryMedia> {
        val selected=report.safeSelection(ids)
        val keepers=report.groups.filter {group->group.files.any {it.media.id in ids}}.map {group->group.files.first {it.media.id !in ids}}
        for(file in selected+keepers) require(hash(context,file.media).first==file.sha256)
        return selected.map {it.media}
    }
    internal fun fingerprint(image:Bitmap):Visual {
        val small=Bitmap.createBitmap(9,8,Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(image,null,Rect(0,0,9,8),Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels=IntArray(72);small.getPixels(pixels,0,9,0,0,9,8);small.recycle()
        fun light(color:Int)=((color ushr 16 and 255)*299+(color ushr 8 and 255)*587+(color and 255)*114)/1000
        var hash=0L
        for(y in 0..7) for(x in 0..7) if(light(pixels[y*9+x])-light(pixels[y*9+x+1])>3) hash=hash or (1L shl (y*8+x))
        return Visual(hash,image.width.toFloat()/image.height,pixels)
    }
    internal fun looksSimilar(a:Visual,b:Visual):Boolean {
        if(abs(a.ratio/b.ratio-1)>.03f || java.lang.Long.bitCount(a.hash xor b.hash)>4) return false
        var error=0.0
        a.pixels.indices.forEach {i->
            for(shift in listOf(0,8,16,24)) {val delta=(a.pixels[i] ushr shift and 255)-(b.pixels[i] ushr shift and 255);error+=delta*delta}
        }
        return sqrt(error/(a.pixels.size*4))<=24
    }
    private class VisualTree {
        data class Entry(val visual:Visual,val group:Int)
        class Node(val hash:Long,val entries:MutableList<Entry>,val children:MutableMap<Int,Node> = mutableMapOf())
        var root:Node?=null
        fun add(visual:Visual,group:Int) {
            val entry=Entry(visual,group);val first=root
            if(first==null) {root=Node(visual.hash,mutableListOf(entry));return}
            var node:Node=first
            while(true) {
                val distance=java.lang.Long.bitCount(node.hash xor visual.hash)
                if(distance==0) {node.entries+=entry;return}
                val child=node.children[distance]
                if(child==null) {node.children[distance]=Node(visual.hash,mutableListOf(entry));return}
                node=child
            }
        }
        fun matches(hash:Long,radius:Int):List<Entry> {
            val found=mutableListOf<Entry>();val queue=ArrayDeque<Node>();root?.let {queue.add(it)}
            while(queue.isNotEmpty()) {
                val node=queue.removeFirst();val distance=java.lang.Long.bitCount(node.hash xor hash)
                if(distance<=radius) found+=node.entries
                node.children.forEach {(edge,child)->if(edge in distance-radius..distance+radius) queue.add(child)}
            }
            return found
        }
    }
}
