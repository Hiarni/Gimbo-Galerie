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
import androidx.compose.material3.MaterialTheme
import de.luma.gallery.data.PhotoTextRenderer
import de.luma.gallery.model.PhotoText

@Composable
fun PhotoTextPreview(bitmap: Bitmap, selected: PhotoText?, onChange: (PhotoText) -> Unit, onFinish: () -> Unit) {
    val strings = appStrings()
    var container by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val scale = minOf(container.width.toFloat() / bitmap.width, container.height.toFloat() / bitmap.height)
    val image = Rect((container.width - bitmap.width * scale) / 2, (container.height - bitmap.height * scale) / 2,
        (container.width + bitmap.width * scale) / 2, (container.height + bitmap.height * scale) / 2)
    val radius = with(LocalDensity.current) { 20.dp.toPx() }
    val color = MaterialTheme.colorScheme.primary
    fun frame(text: PhotoText): Rect {
        val bounds = PhotoTextRenderer.bounds(bitmap.width, bitmap.height, text)
        return Rect(image.left + bounds.left * scale, image.top + bounds.top * scale,
            image.left + bounds.right * scale, image.top + bounds.bottom * scale)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        Image(bitmap.asImageBitmap(), strings.getString(R.string.edit_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = strings.getString(R.string.text_frame_description) }
            .pointerInput(image, selected?.id) {
                var gesture = current
                var moving = false
                detectDragGestures(onDragStart = { at ->
                    gesture = current
                    moving = gesture?.let { frame(it).inflate(radius).contains(at) } == true
                    gesture?.takeIf { moving }?.let {
                        val actual = PhotoTextRenderer.bounds(bitmap.width, bitmap.height, it)
                        gesture = it.copy(x = actual.centerX() / bitmap.width, y = actual.centerY() / bitmap.height)
                    }
                }, onDragEnd = { if (moving) finish() }, onDragCancel = { if (moving) finish() }) { event, delta ->
                    val text = gesture
                    if (moving && text != null && image.width > 0 && image.height > 0) {
                        event.consume()
                        gesture = text.copy(x = (text.x + delta.x / image.width).coerceIn(0f, 1f),
                            y = (text.y + delta.y / image.height).coerceIn(0f, 1f))
                        change(gesture!!)
                    }
                }
            }) { selected?.let { drawRect(color, frame(it).topLeft, frame(it).size, style = Stroke(2.dp.toPx())) } }
    }
}
