package de.luma.gallery.data

import android.media.MediaDataSource
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.Cipher

internal class VaultDataSource(private val repository:VaultRepository,private val key:ByteArray,private val item:VaultItem,file:File,
    private val check:()->Unit):MediaDataSource() {
    private data class Block(val offset:Long,val start:Long,val size:Int)
    private val input=RandomAccessFile(file,"r")
    private val prefix=ByteArray(8)
    private val blocks=ArrayList<Block>()
    private var cachedIndex=-1
    private var cached=ByteArray(0)
    private var closed=false
    init {
        try {
            check();require(input.readInt()==VaultRepository.MAGIC);input.readFully(prefix)
            var position=0L
            while(true) {
                check();val length=input.readInt();require(length in 16..65552)
                if(length==16) {
                    val end=ByteArray(16);input.readFully(end)
                    repository.cipher(key,Cipher.DECRYPT_MODE,"${item.id}:${blocks.size}:end",VaultRepository.nonce(prefix,blocks.size)).doFinal(end)
                    require(input.filePointer==input.length() && position==item.bytes)
                    break
                }
                blocks+=Block(input.filePointer,position,length);position+=length-16
                require(position<=item.bytes && input.filePointer+length<=input.length());input.seek(input.filePointer+length)
            }
        } catch(e:Throwable) {input.close();key.fill(0);throw e}
    }
    @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
        try {
            check();check(!closed);require(position>=0 && offset>=0 && size>=0 && offset+size<=buffer.size)
            if(size==0) return 0
            if(position>=item.bytes) return -1
            var copied=0
            while(copied<size && position+copied<item.bytes) {
                check()
                val current=position+copied
                var low=0;var high=blocks.lastIndex
                while(low<high) {val middle=(low+high+1)/2;if(blocks[middle].start<=current) low=middle else high=middle-1}
                val block=blocks[low]
                if(cachedIndex!=low) {
                    val encrypted=ByteArray(block.size);input.seek(block.offset);input.readFully(encrypted)
                    cached.fill(0);cached=repository.cipher(key,Cipher.DECRYPT_MODE,"${item.id}:$low",VaultRepository.nonce(prefix,low)).doFinal(encrypted);cachedIndex=low
                }
                val within=(current-block.start).toInt();val count=minOf(size-copied,cached.size-within)
                require(count>0);cached.copyInto(buffer,offset+copied,within,within+count);copied+=count
            }
            return copied
        } catch(e:Exception) {throw IOException("Vault media unavailable",e)}
    }
    override fun getSize()=item.bytes
    @Synchronized override fun close() {if(!closed) {closed=true;input.close();cached.fill(0);key.fill(0)}}
}
