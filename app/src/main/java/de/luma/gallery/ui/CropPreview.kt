package de.luma.gallery.ui

import de.luma.gallery.R
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.CropBounds
import de.luma.gallery.model.CropCorner
import kotlin.math.hypot

@Composable
fun CropPreview(bitmap: Bitmap, bounds: CropBounds, lockRatio: Boolean,
    onChange: (CropBounds) -> Unit, onFinish: () -> Unit) {
    val strings = appStrings()
    var container by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(bounds)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val touchRadius = with(LocalDensity.current) { 32.dp.toPx() }
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }
    val handle = with(LocalDensity.current) { 5.dp.toPx() }
    val scale = minOf(container.width.toFloat() / bitmap.width, container.height.toFloat() / bitmap.height)
    val image = Rect((container.width - bitmap.width * scale) / 2, (container.height - bitmap.height * scale) / 2,
        (container.width + bitmap.width * scale) / 2, (container.height + bitmap.height * scale) / 2)
    fun frame(b: CropBounds) = Rect(image.left + b.left * image.width, image.top + b.top * image.height,
        image.left + b.right * image.width, image.top + b.bottom * image.height)
    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        Image(bitmap.asImageBitmap(), strings.getString(R.string.crop_image), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = strings.getString(R.string.crop_frame_description) }
            .pointerInput(image, lockRatio) {
                var corner: CropCorner? = null
                var moving = false
                var gestureBounds = current
                detectDragGestures(onDragStart = { point ->
                    gestureBounds = current
                    val rect = frame(current)
                    val nearest = CropCorner.entries.minByOrNull { candidate ->
                        val x = if (candidate.left) rect.left else rect.right
                        val y = if (candidate.top) rect.top else rect.bottom
                        hypot(point.x - x, point.y - y)
                    }
                    corner = nearest?.takeIf {
                        hypot(point.x - if (it.left) rect.left else rect.right,
                            point.y - if (it.top) rect.top else rect.bottom) <= touchRadius
                    }
                    moving = corner == null && rect.contains(point)
                }, onDragEnd = { if (corner != null || moving) finish() },
                    onDragCancel = { if (corner != null || moving) finish() }) { event, delta ->
                    if (image.width > 0 && image.height > 0 && (moving || corner != null)) {
                        event.consume()
                        val dx = delta.x / image.width
                        val dy = delta.y / image.height


                        gestureBounds = corner?.let { gestureBounds.resize(it, dx, dy, lockRatio) } ?: gestureBounds.move(dx, dy)
                        change(gestureBounds)
                    }
                }
            }) {
            val rect = frame(bounds)
            val shade = Color.Black.copy(alpha = .55f)
            drawRect(shade, image.topLeft, androidx.compose.ui.geometry.Size(image.width, rect.top - image.top))
            drawRect(shade, Offset(image.left, rect.bottom), androidx.compose.ui.geometry.Size(image.width, image.bottom - rect.bottom))
            drawRect(shade, Offset(image.left, rect.top), androidx.compose.ui.geometry.Size(rect.left - image.left, rect.height))
            drawRect(shade, Offset(rect.right, rect.top), androidx.compose.ui.geometry.Size(image.right - rect.right, rect.height))
            drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(stroke))
            for (part in 1..2) {
                val x = rect.left + rect.width * part / 3
                val y = rect.top + rect.height * part / 3
                drawLine(Color.White.copy(alpha = .45f), Offset(x, rect.top), Offset(x, rect.bottom), stroke / 2)
                drawLine(Color.White.copy(alpha = .45f), Offset(rect.left, y), Offset(rect.right, y), stroke / 2)
            }
            for (point in listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)) {
                drawCircle(Color.Black.copy(alpha = .5f), handle + stroke, point)
                drawCircle(Color.White, handle, point)
            }
        }
    }
}
