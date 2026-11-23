package de.luma.gallery.data

import de.luma.gallery.model.VideoKeyframe


internal class VideoVolumeEnvelope(keyframes: List<VideoKeyframe>) {
    private val frames=keyframes.sortedBy{it.timeMs}
    private var previousTime=Long.MIN_VALUE
    private var previousValue=1f
    fun value(timeMs: Long): Float {
        if(frames.isEmpty())return 1f
        if(timeMs==previousTime)return previousValue
        val next=frames.binarySearch{it.timeMs.compareTo(timeMs)}
        val value=if(next>=0)frames[next].volume else {
            val index=-next-1
            when(index){0->frames.first().volume;frames.size->frames.last().volume;else->{val a=frames[index-1];val b=frames[index];a.volume+(b.volume-a.volume)*(timeMs-a.timeMs).toFloat()/(b.timeMs-a.timeMs)}}
        }
        previousTime=timeMs;previousValue=value;return value
    }
}
