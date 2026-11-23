package de.luma.gallery.data

import java.io.*
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.*


internal object RasterEncoders {
    fun png(raster: DiskRaster,output: OutputStream,check: ()->Unit) {
        val out=DataOutputStream(output)
        out.write(byteArrayOf(137.toByte(),80,78,71,13,10,26,10))
        fun chunk(type: String,bytes: ByteArray) {val tag=type.toByteArray(Charsets.US_ASCII);out.writeInt(bytes.size);out.write(tag);out.write(bytes);val crc=CRC32();crc.update(tag);crc.update(bytes);out.writeInt(crc.value.toInt())}
        val header=ByteArrayOutputStream();DataOutputStream(header).apply{writeInt(raster.width);writeInt(raster.height);writeByte(8);writeByte(6);writeByte(0);writeByte(0);writeByte(0)}
        chunk("IHDR",header.toByteArray())
        val sink=object: OutputStream(){val bytes=ByteArrayOutputStream();override fun write(b: Int){bytes.write(b);if(bytes.size()>=65536)flush()};override fun write(b: ByteArray,off: Int,len: Int){var offset=off;var remaining=len;while(remaining>0){val n=minOf(65536-bytes.size(),remaining);bytes.write(b,offset,n);offset+=n;remaining-=n;if(bytes.size()==65536)flush()}};override fun flush(){if(bytes.size()>0){chunk("IDAT",bytes.toByteArray());bytes.reset()}};override fun close(){flush()}}
        val deflater=Deflater(6)
        try {DeflaterOutputStream(sink,deflater,65536).use{zip->
            val row=ByteArray(raster.width*4+1)
            for(y in 0 until raster.height){check();val pixels=raster.row(y);row[0]=0;for(x in 0 until raster.width){val p=pixels[x];val i=1+x*4;row[i]=(p ushr 16).toByte();row[i+1]=(p ushr 8).toByte();row[i+2]=p.toByte();row[i+3]=(p ushr 24).toByte()};zip.write(row)}
        }} finally {deflater.end()}
        chunk("IEND",ByteArray(0));out.flush()
    }
    fun jpeg(raster: DiskRaster,quality: Int,output: OutputStream,check: ()->Unit) {
        val out=DataOutputStream(output)
        fun marker(code: Int){out.writeShort(0xff00 or code)}
        fun segment(code: Int,write: DataOutputStream.()->Unit){val data=ByteArrayOutputStream();DataOutputStream(data).use{it.write()};marker(code);out.writeShort(data.size()+2);out.write(data.toByteArray())}
        val scale=if(quality<50)5000/quality else 200-quality*2
        val quant=listOf(LUMA,CHROMA).map{table->IntArray(64){((table[it]*scale+50)/100).coerceIn(1,255)}}
        marker(0xd8)
        segment(0xdb){for(t in 0..1){writeByte(t);for(i in ZIGZAG)writeByte(quant[t][i])}}
        segment(0xc0){writeByte(8);writeShort(raster.height);writeShort(raster.width);writeByte(3);for(c in 1..3){writeByte(c);writeByte(0x11);writeByte(if(c==1)0 else 1)}}
        val acSymbols=(listOf(0,240)+(0..15).flatMap{run->(1..10).map{size->run*16+size}}).sorted()
        val acCodes=IntArray(256);acSymbols.forEachIndexed{i,s->acCodes[s]=i}
        segment(0xc4){writeByte(0);for(length in 1..16)writeByte(if(length==4)12 else 0);for(i in 0..11)writeByte(i)
            writeByte(0x10);for(length in 1..16)writeByte(if(length==8)162 else 0);for(symbol in acSymbols)writeByte(symbol)}
        segment(0xda){writeByte(3);for(c in 1..3){writeByte(c);writeByte(0)};writeByte(0);writeByte(63);writeByte(0)}
        var bits=0L;var count=0
        fun emit(value: Int,length: Int){if(length==0)return;bits=(bits shl length) or (value.toLong() and ((1L shl length)-1));count+=length
            while(count>=8){count-=8;val byte=(bits ushr count).toInt() and 255;out.writeByte(byte);if(byte==255)out.writeByte(0)}}
        fun category(value: Int)=if(value==0)0 else 32-Integer.numberOfLeadingZeros(abs(value))
        fun number(value: Int,n: Int){emit(if(value<0)value+(1 shl n)-1 else value,n)}
        val previous=IntArray(3);val block=Array(3){DoubleArray(64)};val rows=DoubleArray(64);val coefficients=IntArray(64)
        for(y in 0 until raster.height step 8){val strip=Array(8){raster.row(minOf(y+it,raster.height-1))};for(x in 0 until raster.width step 8){check()
            for(dy in 0..7)for(dx in 0..7){val p=strip[dy][minOf(x+dx,raster.width-1)];val alpha=(p ushr 24)/255.0
                val r=(p ushr 16 and 255)*alpha+255*(1-alpha);val g=(p ushr 8 and 255)*alpha+255*(1-alpha);val b=(p and 255)*alpha+255*(1-alpha)
                val i=dy*8+dx;block[0][i]=.299*r+.587*g+.114*b-128;block[1][i]=-.168736*r-.331264*g+.5*b;block[2][i]=.5*r-.418688*g-.081312*b}
            for(c in 0..2){val values=block[c];val q=quant[if(c==0)0 else 1]
                for(row in 0..7)for(u in 0..7){var sum=0.0;for(i in 0..7)sum+=BASIS[u][i]*values[row*8+i];rows[row*8+u]=sum}
                for(v in 0..7)for(u in 0..7){var sum=0.0;for(i in 0..7)sum+=BASIS[v][i]*rows[i*8+u];coefficients[v*8+u]=(sum/q[v*8+u]).roundToInt()}
                val difference=coefficients[0]-previous[c];previous[c]=coefficients[0];val dc=category(difference);emit(dc,4);number(difference,dc)
                var run=0
                for(index in 1..63){val value=coefficients[ZIGZAG[index]];if(value==0){run++;continue};while(run>=16){emit(acCodes[240],8);run-=16};val size=category(value);check(size<=10);emit(acCodes[run*16+size],8);number(value,size);run=0}
                if(run>0)emit(acCodes[0],8)
            }
        }
        }
        if(count>0)emit((1 shl (8-count))-1,8-count)
        marker(0xd9);out.flush()
    }
    private val BASIS=Array(8){u->DoubleArray(8){x->.5*(if(u==0)1/sqrt(2.0) else 1.0)*cos((2*x+1)*u*PI/16)}}
    private val ZIGZAG=intArrayOf(0,1,8,16,9,2,3,10,17,24,32,25,18,11,4,5,12,19,26,33,40,48,41,34,27,20,13,6,7,14,21,28,35,42,49,56,57,50,43,36,29,22,15,23,30,37,44,51,58,59,52,45,38,31,39,46,53,60,61,54,47,55,62,63)
    private val LUMA=intArrayOf(16,11,10,16,24,40,51,61,12,12,14,19,26,58,60,55,14,13,16,24,40,57,69,56,14,17,22,29,51,87,80,62,18,22,37,56,68,109,103,77,24,35,55,64,81,104,113,92,49,64,78,87,103,121,120,101,72,92,95,98,112,100,103,99)
    private val CHROMA=intArrayOf(17,18,24,47,99,99,99,99,18,21,26,66,99,99,99,99,24,26,56,99,99,99,99,99,47,66,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99)
}
