package de.luma.gallery.data

import android.content.Context
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SourceProbe {
    suspend fun inspect(context: Context, source: GalleryMedia): SourceInfo = withContext(Dispatchers.IO) {
        GalleryRepository(context).checkSource(source)
        if (source.isVideo) MediaMetadataRetriever().use { reader ->
            reader.setDataSource(context, source.uri)
            val width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            requireMessage(width > 0 && height > 0, R.string.source_invalid)
            val rotation = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation.mod(180) == 90) SourceInfo(height, width) else SourceInfo(width, height)
        } else {
            var info: SourceInfo? = null
            val input = if (source.isDemo) ImageDecoder.createSource(context.assets, source.uri.path!!.removePrefix("/android_asset/"))
                else ImageDecoder.createSource(context.contentResolver, source.uri)
            val bitmap = ImageDecoder.decodeBitmap(input) { decoder, image, _ ->
                info = SourceInfo(image.size.width, image.size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; decoder.setTargetSize(1, 1)
            }
            bitmap.recycle()
            requireMessage(info != null && info!!.width > 0 && info!!.height > 0, R.string.source_invalid)
            info!!
        }
    }
}
