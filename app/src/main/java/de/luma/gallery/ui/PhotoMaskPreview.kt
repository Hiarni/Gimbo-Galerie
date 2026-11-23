package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun PhotoMaskPreview(bitmap: Bitmap, selected: PhotoMask?, onChange: (PhotoMask) -> Unit, onFinish: () -> Unit) {
    val strings = appStrings()
    var container by remember { mutableStateOf(IntSize.Zero) }
    val current by rememberUpdatedState(selected); val change by rememberUpdatedState(onChange); val finish by rememberUpdatedState(onFinish)
    val scale = minOf(container.width.toFloat() / bitmap.width, container.height.toFloat() / bitmap.height)
    val left = (container.width - bitmap.width * scale) / 2; val top = (container.height - bitmap.height * scale) / 2
    val width = bitmap.width * scale; val height = bitmap.height * scale
    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        Image(bitmap.asImageBitmap(), strings.getString(R.string.edit_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = strings.getString(if (selected?.shape in listOf(MaskShape.FREEHAND, MaskShape.BRUSH)) R.string.mask_draw_canvas else R.string.mask_canvas) }
            .pointerInput(selected?.id, container, bitmap.width, bitmap.height) {
                var moving = false
                var drawing = false
                var gesture: PhotoMask? = null
                detectDragGestures(onDragStart = { at ->
                    gesture = current
                    drawing = gesture?.shape in listOf(MaskShape.FREEHAND, MaskShape.BRUSH)
                    if (drawing && width > 0 && height > 0) {
                        gesture = gesture!!.copy(x = .5f, y = .5f, width = 1f, height = 1f, points = listOf(MarkupPoint(((at.x-left)/width).coerceIn(0f,1f), ((at.y-top)/height).coerceIn(0f,1f))))
                        change(gesture!!)
                    }
                    moving = gesture != null && width > 0 && height > 0 && at.x in left..(left + width) && at.y in top..(top + height)
                }, onDragEnd = { if (moving) finish(); moving = false }, onDragCancel = { if (moving) finish(); moving = false }) { event, delta ->
                    if (moving) gesture?.let { mask ->
                        event.consume()
                        gesture = if (drawing) {
                            val points = if (mask.points.size >= 256) mask.points.filterIndexed { i, _ -> i % 2 == 0 } else mask.points
                            mask.copy(points = points + MarkupPoint(((event.position.x-left)/width).coerceIn(0f,1f), ((event.position.y-top)/height).coerceIn(0f,1f)))
                        } else mask.copy(x = (mask.x + delta.x / width).coerceIn(0f, 1f), y = (mask.y + delta.y / height).coerceIn(0f, 1f))
                        change(gesture!!)
                    }
                }
            }) {
            selected?.let { mask ->
                val origin = Offset(left + (mask.x - mask.width / 2) * width, top + (mask.y - mask.height / 2) * height)
                val area = Size(mask.width * width, mask.height * height)
                listOf(Color.Black to 4.dp.toPx(), Color.White to 2.dp.toPx()).forEach { (color, stroke) ->
                    if (mask.shape in listOf(MaskShape.FREEHAND, MaskShape.BRUSH)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            mask.points.forEachIndexed { i, p -> val px = left + (mask.x + (p.x - .5f) * mask.width) * width; val py = top + (mask.y + (p.y - .5f) * mask.height) * height
                                if (i == 0) moveTo(px, py) else lineTo(px, py) }
                            if (mask.shape == MaskShape.FREEHAND) close()
                        }
                        drawPath(path, color, style = Stroke(stroke))
                    } else if (mask.shape == MaskShape.ELLIPSE) drawOval(color, origin, area, style = Stroke(stroke)) else drawRect(color, origin, area, style = Stroke(stroke))
                }
            }
        }
    }
}
