package de.luma.gallery.model

import de.luma.gallery.R
import java.time.*

data class MediaMemory(val key:String,val labelRes:Int,val year:Int,val month:Int,val media:List<GalleryMedia>)
fun mediaMemories(media:List<GalleryMedia>,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<MediaMemory> {
    val dated=media.filter {it.timestamp>0 && !it.isScreenshot}.map {it to Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()}.filter {it.second<today}
    val sameDay=dated.filter {it.second.month==today.month && it.second.dayOfMonth==today.dayOfMonth && it.second.year<today.year}
        .groupBy {it.second.year}.toSortedMap(compareByDescending {it}).map { (year,items)->MediaMemory("day:$year",R.string.memory_on_this_day,year,today.monthValue,items.map {it.first})}.take(3)
    val sameMonth=dated.filter {it.second.month==today.month && it.second.year<today.year}.groupBy {it.second.year}.toSortedMap(compareByDescending {it})
        .filter {(_,items)->items.size>=2}.map {(year,items)->MediaMemory("month:$year:${today.monthValue}",R.string.memory_month,year,today.monthValue,items.map {it.first})}.take(3)
    val recent=dated.filter {YearMonth.from(it.second)<YearMonth.from(today)}.groupBy {YearMonth.from(it.second)}.toSortedMap(compareByDescending {it})
        .filter {(_,items)->items.size>=2}.map {(month,items)->MediaMemory("past:$month",R.string.memory_month,month.year,month.monthValue,items.map {it.first})}.take(6)
    val seen=mutableSetOf<Set<String>>()
    return (sameDay+sameMonth+recent).filter {seen.add(it.media.map {m->m.id}.toSet())}.take(9)
}

enum class PhotoMood(val labelRes:Int,val background:Long) {
    WARM(R.string.mood_warm,0xFFF3DDC5), COOL(R.string.mood_cool,0xFFDDE9F2), QUIET(R.string.mood_quiet,0xFFE5E1D9), VIBRANT(R.string.mood_vibrant,0xFFEAE0FA), MONOCHROME(R.string.mood_monochrome,0xFF202027)
}
data class MoodPhoto(val media:GalleryMedia,val mood:PhotoMood,val score:Float)
