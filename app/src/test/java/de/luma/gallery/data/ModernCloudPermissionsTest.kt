package de.luma.gallery.data

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(sdk=[35])
class ModernCloudPermissionsTest:GalleryOperationsTest() {
    @Test fun fullPhotoPermissionAllowsReviewWithoutFullVideoPermission()=runBlocking {
        val cloud=cloud();cloud.synchronize();originals.files.remove(1)?.delete()
        shadowOf(app).denyPermissions(android.Manifest.permission.READ_MEDIA_VIDEO)
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        assertEquals(1,cloud.deletionReview().size)
        shadowOf(app).denyPermissions(android.Manifest.permission.READ_MEDIA_IMAGES)
        shadowOf(app).grantPermissions(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        assertTrue(cloud.deletionReview().isEmpty())
    }
}
