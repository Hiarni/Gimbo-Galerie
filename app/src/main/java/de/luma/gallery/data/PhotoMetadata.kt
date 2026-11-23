package de.luma.gallery.data

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import de.luma.gallery.localization.requireMessage
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import de.luma.gallery.model.*
import de.luma.gallery.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoMetadata {
    private val camera = listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE)
    private val location = listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD)
    private fun read(context: Context, source: GalleryMedia, includeLocation: Boolean = false): ExifInterface {
        val uri = if (includeLocation && source.uri.authority == MediaStore.AUTHORITY) {
            requireMessage(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED,
                R.string.metadata_permission_required)
            MediaStore.setRequireOriginal(source.uri)
        } else source.uri
        val stream = if (source.isDemo) context.assets.open(source.uri.path!!.removePrefix("/android_asset/")) else context.contentResolver.openInputStream(uri)
        return requireNotNull(stream).use(::ExifInterface)
    }
    suspend fun info(context: Context, source: GalleryMedia): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        if (source.isVideo) emptyList() else read(context, source).let { exif ->
            listOf(ExifInterface.TAG_MAKE to R.string.camera_make, ExifInterface.TAG_MODEL to R.string.camera_model,
                ExifInterface.TAG_DATETIME_ORIGINAL to R.string.capture_date, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to R.string.camera_iso,
                ExifInterface.TAG_EXPOSURE_TIME to R.string.camera_exposure, ExifInterface.TAG_F_NUMBER to R.string.camera_aperture,
                ExifInterface.TAG_FOCAL_LENGTH to R.string.camera_focal).mapNotNull { (key, label) -> exif.getAttribute(key)?.let { label to it } }
        }
    }
    fun copy(context: Context, source: GalleryMedia, destination: Uri, mode: MetadataMode, width: Int, height: Int) {
        if (mode == MetadataMode.NONE) return
        val original = read(context, source, mode == MetadataMode.CAMERA_AND_LOCATION)
        val temp = java.io.File.createTempFile("luma_metadata_", ".jpg", context.cacheDir)
        try {
            context.contentResolver.openInputStream(destination)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } } ?: throw java.io.IOException()
            val edited = ExifInterface(temp.path)
            for (key in camera + if (mode == MetadataMode.CAMERA_AND_LOCATION) location else emptyList())
                edited.setAttribute(key, original.getAttribute(key))

            edited.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            edited.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
            edited.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
            edited.setAttribute(ExifInterface.TAG_COLOR_SPACE, "1")
            edited.saveAttributes()
            context.contentResolver.openOutputStream(destination, "wt")?.use { output -> temp.inputStream().use { it.copyTo(output) } } ?: throw java.io.IOException()
        } finally { temp.delete() }
    }
}
