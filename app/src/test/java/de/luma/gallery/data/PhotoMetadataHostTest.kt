package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import de.luma.gallery.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoMetadataHostTest {
    @Test fun cameraMetadataOmitsLocationUnlessExplicitlySelectedAndKeepsSourceBytes() {
        val context = RuntimeEnvironment.getApplication()
        val files = List(3) { File.createTempFile("metadata_", ".jpg", context.cacheDir) }
        try {
            val bitmap = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            try { files.forEach { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } } } finally { bitmap.recycle() }
            ExifInterface(files[0].path).apply { setAttribute(ExifInterface.TAG_MAKE, "CameraMaker"); setLatLong(52.52, 13.405); saveAttributes() }
            val original = files[0].readBytes()
            val source = GalleryMedia("photo", Uri.fromFile(files[0]), "Original.jpg", "", 0)
            PhotoMetadata.copy(context, source, Uri.fromFile(files[1]), MetadataMode.CAMERA, 40, 20)
            val camera = ExifInterface(files[1].path)
            assertEquals("CameraMaker", camera.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(camera.latLong)
            assertEquals(ExifInterface.ORIENTATION_NORMAL, camera.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1))
            PhotoMetadata.copy(context, source, Uri.fromFile(files[2]), MetadataMode.CAMERA_AND_LOCATION, 40, 20)
            assertArrayEquals(doubleArrayOf(52.52, 13.405), ExifInterface(files[2].path).latLong, .00001)
            assertArrayEquals(original, files[0].readBytes())
        } finally { files.forEach { it.delete() } }
    }
    @Test fun revokedMediaLocationPermissionStopsGpsCopyBeforeTouchingTheDestination() {
        val context = RuntimeEnvironment.getApplication()
        org.robolectric.Shadows.shadowOf(context).denyPermissions(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        val destination = File.createTempFile("metadata_permission_", ".jpg", context.cacheDir)
        val bytes = byteArrayOf(1, 2, 3, 4)
        try {
            destination.writeBytes(bytes)
            val source = GalleryMedia("photo", Uri.parse("content://media/external/images/media/100"), "Original.jpg", "", 0)
            PhotoMetadata.copy(context, source, Uri.fromFile(destination), MetadataMode.NONE, 40, 20)
            val failure = runCatching { PhotoMetadata.copy(context, source, Uri.fromFile(destination), MetadataMode.CAMERA_AND_LOCATION, 40, 20) }.exceptionOrNull()
            assertTrue(failure is de.luma.gallery.localization.ResourceFailure)
            assertEquals(de.luma.gallery.R.string.metadata_permission_required, (failure as de.luma.gallery.localization.ResourceFailure).messageRes)
            assertArrayEquals(bytes, destination.readBytes())
        } finally { destination.delete() }
    }

}
