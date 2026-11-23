package de.luma.gallery.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import de.luma.gallery.R
import de.luma.gallery.localization.appLocale
import de.luma.gallery.localization.localizedResources
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

object MediaDetails {
    suspend fun read(context: Context, media: GalleryMedia, location: Boolean = false): List<Pair<Int,String>> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val strings = localizedResources(context)
        val out = mutableListOf(R.string.info_filename to media.name, R.string.album to media.album,
            R.string.info_uri to media.uri.toString())
        fun add(label: Int, value: String?) { value?.takeIf { it.isNotBlank() }?.let { out += label to it } }
        add(R.string.info_format, resolver.getType(media.uri))
        if (media.size >= 0) add(R.string.info_bytes, android.text.format.Formatter.formatFileSize(context,media.size) + " (${media.size})")
        fun time(value: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.MEDIUM,strings.appLocale).format(Date(value))
        if (media.timestamp > 0) add(R.string.capture_date,time(media.timestamp))
        runCatching { SourceProbe.inspect(context,media) }.getOrNull()?.let { add(R.string.info_resolution,"${it.width} × ${it.height}") }
        if (media.uri.authority == MediaStore.AUTHORITY) runCatching {
            val columns = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH,MediaStore.MediaColumns.DATE_ADDED,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            resolver.query(media.uri,columns,Bundle(),null)?.use { c -> if(c.moveToFirst()) {
                add(R.string.info_path,c.getString(0))
                if(!c.isNull(1)&&c.getLong(1)>0) add(R.string.info_added,time(c.getLong(1)*1000))
                if(!c.isNull(2)&&c.getLong(2)>0) add(R.string.info_modified,time(c.getLong(2)*1000))
                add(R.string.info_owner,c.getString(3))
            } }
        }
        if (!media.isVideo) {
            runCatching { PhotoMetadata.info(context,media) }.getOrNull()?.let { out += it }
            runCatching {
                val uri = if(location && media.uri.authority == MediaStore.AUTHORITY) MediaStore.setRequireOriginal(media.uri) else media.uri
                resolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    listOf(ExifInterface.TAG_LENS_MODEL to R.string.info_lens, ExifInterface.TAG_ORIENTATION to R.string.info_rotation,
                        ExifInterface.TAG_COLOR_SPACE to R.string.info_color_space,ExifInterface.TAG_FLASH to R.string.info_flash,
                        ExifInterface.TAG_WHITE_BALANCE to R.string.info_white_balance,ExifInterface.TAG_SOFTWARE to R.string.info_software,
                        ExifInterface.TAG_COPYRIGHT to R.string.info_copyright,ExifInterface.TAG_IMAGE_DESCRIPTION to R.string.info_description)
                        .forEach { (tag,label) -> add(label,exif.getAttribute(tag)) }
                    if(location) exif.latLong?.let { add(R.string.info_location,"${it[0]}, ${it[1]}") }
                }
            }
        } else {
            runCatching { MediaMetadataRetriever().use { m ->
                m.setDataSource(context,media.uri)
                listOf(MediaMetadataRetriever.METADATA_KEY_BITRATE to R.string.info_bitrate,
                    MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE to R.string.info_fps,
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION to R.string.info_rotation,
                    MediaMetadataRetriever.METADATA_KEY_DATE to R.string.info_created)
                    .forEach { (key,label) -> add(label,m.extractMetadata(key)) }
                if(android.os.Build.VERSION.SDK_INT>=30) {
                    add(R.string.info_transfer,m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER))
                    add(R.string.info_color_space,m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD))
                }
                if(location) add(R.string.info_location,m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            } }
            add(R.string.info_duration,"%.3f s".format(strings.appLocale,media.durationMs/1000.0))
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context,media.uri,null)
                val codecs = mutableListOf<String>()
                for(i in 0 until extractor.trackCount) {
                    val f=extractor.getTrackFormat(i)
                    f.getString(MediaFormat.KEY_MIME)?.let(codecs::add)
                    fun number(key:String,label:Int) { if(f.containsKey(key)) runCatching { add(label,f.getInteger(key).toString()) } }
                    number(MediaFormat.KEY_SAMPLE_RATE,R.string.info_sample_rate);number(MediaFormat.KEY_CHANNEL_COUNT,R.string.info_channels)
                    number(MediaFormat.KEY_FRAME_RATE,R.string.info_fps)
                }
                add(R.string.info_codecs,codecs.distinct().joinToString(" / "))
            } catch (_: Exception) { } finally { extractor.release() }
        }
        out.distinct()
    }
}
