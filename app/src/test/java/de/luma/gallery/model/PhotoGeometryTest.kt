package de.luma.gallery.model

import org.junit.Assert.*
import org.junit.Test

class PhotoGeometryTest {
    @Test fun landscapeAndPortraitRatiosFitInsideImage() {
        val landscape = CropBounds.centered(2f, 1f)
        assertEquals(.25f, landscape.left, .00001f)
        assertEquals(.75f, landscape.right, .00001f)
        assertEquals(1f, landscape.height, .00001f)
        val portrait = CropBounds.centered(.5f, 1f)
        assertEquals(.25f, portrait.top, .00001f)
        assertEquals(.75f, portrait.bottom, .00001f)
        assertEquals(CropBounds(), CropBounds.centered(2f, null))
    }

    @Test fun movingAgainstEdgesPreservesSize() {
        val bounds = CropBounds(.2f, .3f, .6f, .8f)
        val moved = bounds.move(100f, -100f)
        assertEquals(1f, moved.right, .00001f)
        assertEquals(0f, moved.top, .00001f)
        assertEquals(bounds.width, moved.width, .00001f)
        assertEquals(bounds.height, moved.height, .00001f)
    }

    @Test fun freeCornerResizeCannotCrossOppositeCornerOrImageEdge() {
        val bounds = CropBounds(.2f, .2f, .8f, .8f)
        for (corner in CropCorner.entries) {
            for (delta in listOf(-100f, -.1f, .1f, 100f)) {
                val result = bounds.resize(corner, delta, -delta, false)
                assertTrue(result.width >= .04999f && result.height >= .04999f)
                assertEquals(if (corner.left) bounds.right else bounds.left,
                    if (corner.left) result.right else result.left, .00001f)
                assertEquals(if (corner.top) bounds.bottom else bounds.top,
                    if (corner.top) result.bottom else result.top, .00001f)
            }
        }
    }

    @Test fun lockedResizeKeepsAspectAndAnchorEvenAtBoundaries() {
        val bounds = CropBounds(.2f, .3f, .8f, .6f)
        for (corner in CropCorner.entries) for (dx in listOf(-10f, -.1f, .1f, 10f)) {
            val result = bounds.resize(corner, dx, dx / 2, true)
            assertEquals(bounds.width / bounds.height, result.width / result.height, .0001f)
            assertEquals(if (corner.left) bounds.right else bounds.left,
                if (corner.left) result.right else result.left, .00001f)
            assertEquals(if (corner.top) bounds.bottom else bounds.top,
                if (corner.top) result.bottom else result.top, .00001f)
        }
    }

    @Test fun draggingAlongLockedDiagonalTracksTheFingerWithoutDamping() {
        val bounds = CropBounds(.2f, .3f, .8f, .6f)
        val smaller = bounds.resize(CropCorner.BOTTOM_RIGHT, -.2f, -.1f, true)
        assertEquals(.4f, smaller.width, .00001f)
        assertEquals(.2f, smaller.height, .00001f)
        assertEquals(.2f, smaller.left, .00001f)
        assertEquals(.3f, smaller.top, .00001f)
    }

    @Test fun cropUsesSameRelativeRegionAtDifferentExportResolutions() {
        val bounds = CropBounds(.1f, .2f, .7f, .9f)
        assertEquals(PixelCrop(10, 40, 60, 140), bounds.pixels(100, 200))
        assertEquals(PixelCrop(100, 400, 600, 1400), bounds.pixels(1000, 2000))
        assertEquals(PixelCrop(0, 0, 1, 1), CropBounds(.9f, .9f, 1f, 1f).pixels(1, 1))
    }

    @Test fun rotationAndMirroringKeepCropAttachedAndFourTurnsRestoreRegion() {
        val recipe = PhotoRecipe(cropBounds = CropBounds(.1f, .2f, .6f, .8f), cropRatio = 4f / 3)
        val turned = recipe.rotatedClockwise()
        assertEquals(.2f, turned.cropBounds!!.left, .00001f)
        assertEquals(.1f, turned.cropBounds.top, .00001f)
        assertEquals(.75f, turned.cropRatio!!, .00001f)
        val restored = (1..4).fold(recipe) { r, _ -> r.rotatedClockwise() }
        assertEquals(recipe.quarterTurns, restored.quarterTurns)
        assertEquals(recipe.cropBounds!!.left, restored.cropBounds!!.left, .00001f)
        val mirrored = recipe.mirrored()
        assertEquals(.4f, mirrored.cropBounds!!.left, .00001f)
        assertEquals(.9f, mirrored.cropBounds.right, .00001f)
        assertEquals(3, mirrored.rotatedClockwise().quarterTurns)
        assertEquals(.2f, mirrored.rotatedClockwise().cropBounds!!.left, .00001f)
    }

    @Test fun outputDimensionsNeverUpscaleAndFitEveryOfferedSize() {
        for (edge in listOf(1280, 1920, 2560, 4096)) {
            val options = PhotoExportOptions(maxEdge = edge)
            assertEquals(edge to edge / 2, fitWithin(8192, 4096, options.maxEdge))
            assertEquals(600 to 900, fitWithin(600, 900, options.maxEdge))
        }
    }

    @Test fun invalidGeometryAndExportOptionsAreRejected() {
        for (make in listOf<() -> Any>({ CropBounds(right = 0f) }, { CropBounds(left = Float.NaN) },
            { CropBounds(top = -1f) }, { PhotoExportOptions(maxEdge = 0) }, { PhotoExportOptions(quality = 101) })) {
            assertThrows(IllegalArgumentException::class.java) { make() }
        }
    }
}
