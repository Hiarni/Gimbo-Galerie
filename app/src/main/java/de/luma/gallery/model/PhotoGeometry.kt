package de.luma.gallery.model

import de.luma.gallery.R
import kotlin.math.roundToInt


data class CropBounds(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)
    }
    val width get() = right - left
    val height get() = bottom - top

    fun move(dx: Float, dy: Float): CropBounds {
        val x = dx.coerceIn(-left, 1 - right)
        val y = dy.coerceIn(-top, 1 - bottom)
        return CropBounds((left + x).coerceAtLeast(0f), (top + y).coerceAtLeast(0f),
            (right + x).coerceAtMost(1f), (bottom + y).coerceAtMost(1f))
    }


    fun resize(corner: CropCorner, dx: Float, dy: Float, lockRatio: Boolean): CropBounds {
        val horizontal = if (corner.left) -dx else dx
        val vertical = if (corner.top) -dy else dy
        val maxWidth = if (corner.left) right else 1 - left
        val maxHeight = if (corner.top) bottom else 1 - top
        val w: Float
        val h: Float
        if (lockRatio) {
            val ratio = width / height
            val delta = (horizontal + vertical / ratio) / (1 + 1 / (ratio * ratio))
            val maximum = minOf(maxWidth, maxHeight * ratio)
            w = (width + delta).coerceIn(minOf(.05f, maximum), maximum)
            h = w / ratio
        } else {
            w = (width + horizontal).coerceIn(minOf(.05f, maxWidth), maxWidth)
            h = (height + vertical).coerceIn(minOf(.05f, maxHeight), maxHeight)
        }
        return CropBounds(if (corner.left) (right - w).coerceAtLeast(0f) else left,
            if (corner.top) (bottom - h).coerceAtLeast(0f) else top,
            if (corner.left) right else (left + w).coerceAtMost(1f),
            if (corner.top) bottom else (top + h).coerceAtMost(1f))
    }

    fun pixels(imageWidth: Int, imageHeight: Int): PixelCrop {
        require(imageWidth > 0 && imageHeight > 0)
        val x = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val y = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val endX = (right * imageWidth).roundToInt().coerceIn(x + 1, imageWidth)
        val endY = (bottom * imageHeight).roundToInt().coerceIn(y + 1, imageHeight)
        return PixelCrop(x, y, endX - x, endY - y)
    }

    fun withSize(newWidth: Float, newHeight: Float): CropBounds {
        val w = newWidth.coerceIn(.000001f, 1f)
        val h = newHeight.coerceIn(.000001f, 1f)
        val x = ((left + right - w) / 2).coerceIn(0f, 1 - w)
        val y = ((top + bottom - h) / 2).coerceIn(0f, 1 - h)
        return CropBounds(x, y, (x + w).coerceAtMost(1f), (y + h).coerceAtMost(1f))
    }

    fun mirror() = CropBounds(1 - right, top, 1 - left, bottom)
    fun rotateClockwise() = CropBounds(1 - bottom, left, 1 - top, right)
    fun rotateCounterclockwise() = CropBounds(top, 1 - right, bottom, 1 - left)

    companion object {
        fun centered(imageAspect: Float, ratio: Float?): CropBounds {
            require(imageAspect.isFinite() && imageAspect > 0)
            if (ratio == null) return CropBounds()
            require(ratio.isFinite() && ratio > 0)
            val w = minOf(1f, ratio / imageAspect)
            val h = minOf(1f, imageAspect / ratio)
            return CropBounds((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
        }
    }
}

enum class CropCorner(val left: Boolean, val top: Boolean) {
    TOP_LEFT(true, true), TOP_RIGHT(false, true), BOTTOM_LEFT(true, false), BOTTOM_RIGHT(false, false)
}
data class PixelCrop(val x: Int, val y: Int, val width: Int, val height: Int)

fun PhotoRecipe.boundsFor(imageAspect: Float) = cropBounds ?: CropBounds.centered(imageAspect, cropRatio)
fun PhotoRecipe.rotatedClockwise() = copy(quarterTurns = (quarterTurns + if (flipHorizontal) -1 else 1).mod(4),
    cropRatio = cropRatio?.let { 1 / it },
    cropBounds = cropBounds?.rotateClockwise())
fun PhotoRecipe.mirrored() = copy(flipHorizontal = !flipHorizontal, cropBounds = cropBounds?.mirror())

enum class PhotoFormat(val label: String, val mime: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"), PNG("PNG", "image/png", "png")
}
enum class MetadataMode(val labelRes: Int) { NONE(R.string.metadata_none), CAMERA(R.string.metadata_camera), CAMERA_AND_LOCATION(R.string.metadata_location) }
data class PhotoExportOptions(val format: PhotoFormat = PhotoFormat.JPEG, val maxEdge: Int = 4096, val quality: Int = 95, val metadata: MetadataMode = MetadataMode.NONE) {
    init { require(maxEdge in listOf(1280, 1920, 2560, 4096, 8192, 16384)); require(quality in 60..100) }
}


fun fitWithin(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxEdge > 0)
    val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height))
    return maxOf(1, (width * scale).roundToInt()) to maxOf(1, (height * scale).roundToInt())
}
