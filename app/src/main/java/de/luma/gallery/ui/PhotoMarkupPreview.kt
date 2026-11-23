package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import java.util.UUID

@Composable
fun PhotoMarkupPreview(bitmap: Bitmap, selected: PhotoMarkup?, tool: MarkupKind, prototype: PhotoMarkup?, canAdd: Boolean,
    onChange: (PhotoMarkup) -> Unit, onFinish: () -> Unit, onCreate: (PhotoMarkup) -> Unit) {
    val strings = appStrings()
    var container by remember { mutableStateOf(IntSize.Zero) }
    var pending by remember { mutableStateOf<PhotoMarkup?>(null) }
    val current by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val finish by rememberUpdatedState(onFinish)
    val template by rememberUpdatedState(prototype)
    val create by rememberUpdatedState(onCreate)
    val scale = minOf(container.width.toFloat() / bitmap.width, container.height.toFloat() / bitmap.height)
    val image = Rect((container.width - bitmap.width * scale) / 2, (container.height - bitmap.height * scale) / 2,
        (container.width + bitmap.width * scale) / 2, (container.height + bitmap.height * scale) / 2)
    val primary = MaterialTheme.colorScheme.primary
    fun position(point: MarkupPoint) = Offset(image.left + point.x * image.width, image.top + point.y * image.height)
    fun frame(mark: PhotoMarkup) = Rect(position(MarkupPoint(mark.left, mark.top)), position(MarkupPoint(mark.right, mark.bottom)))
    Box(Modifier.fillMaxSize().onSizeChanged { container = it }) {
        Image(bitmap.asImageBitmap(), strings.getString(R.string.edit_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = strings.getString(R.string.markup_canvas) }
            .pointerInput(image, selected?.id, tool, canAdd) {
                var moving = false
                var gesture: PhotoMarkup? = null
                fun point(at: Offset) = MarkupPoint(((at.x - image.left) / image.width).coerceIn(0f, 1f),
                    ((at.y - image.top) / image.height).coerceIn(0f, 1f))
                fun end() {
                    pending?.let { mark ->
                        if (mark.stroke || mark.right - mark.left >= .005f && mark.bottom - mark.top >= .005f) create(mark)
                    }
                    pending = null
                    if (moving) finish()
                    moving = false
                }
                try {
                    detectDragGestures(onDragStart = { at ->
                        gesture = current
                        moving = gesture != null && frame(gesture!!).inflate(20.dp.toPx()).contains(at)
                        if (gesture == null && canAdd && image.width > 0 && image.height > 0 && image.contains(at)) {
                            val start = point(at)
                            val points = if (tool in listOf(MarkupKind.BRUSH, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT)) listOf(start) else listOf(start, start)
                            pending = template?.copy(id = UUID.randomUUID().toString(), points = points) ?: PhotoMarkup(UUID.randomUUID().toString(), tool,
                                points = points, color = if (tool == MarkupKind.COVER) 0xFF000000L else 0xFFFF5252L)
                        }
                    }, onDragEnd = { end() }, onDragCancel = { pending = null; if (moving) finish(); moving = false }) { event, delta ->
                        if (moving && image.width > 0 && image.height > 0) {
                            event.consume()
                            gesture = gesture?.moved(delta.x / image.width, delta.y / image.height)
                            gesture?.let(change)
                        } else pending?.let { mark ->
                            event.consume()
                            val next = point(event.position)

                            val existing = if (mark.points.size >= PHOTO_MARKUP_POINTS) mark.points.filterIndexed { i, _ -> i % 2 == 0 } else mark.points
                            pending = mark.copy(points = if (tool in listOf(MarkupKind.BRUSH, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT)) existing + next else listOf(mark.points.first(), next))
                        }
                    }
                } finally { pending = null }
            }) {
            selected?.let { mark ->
                if (mark.kind == MarkupKind.CLONE || mark.kind == MarkupKind.HEAL) {
                    val sample = position(mark.sample)
                    drawCircle(Color.Black, 8.dp.toPx(), sample, style = Stroke(4.dp.toPx()))
                    drawCircle(Color.White, 8.dp.toPx(), sample, style = Stroke(2.dp.toPx()))
                }
                val bounds = frame(mark).inflate(2.dp.toPx())
                drawRect(primary, bounds.topLeft, bounds.size, style = Stroke(2.dp.toPx()))
            }
            pending?.let { mark ->
                val color = Color(mark.color)
                val stroke = Stroke(maxOf(1f, minOf(image.width, image.height) * mark.width), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                val bounds = frame(mark)
                when (mark.kind) {
                    MarkupKind.BRUSH, MarkupKind.CLONE, MarkupKind.HEAL, MarkupKind.INPAINT -> {
                        val path = Path().apply { mark.points.forEachIndexed { index, p -> val at = position(p)
                            if (index == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y) } }
                        drawPath(path, color, style = stroke)
                    }
                    MarkupKind.ARROW -> {
                        val from = position(mark.points.first()); val to = position(mark.points.last())
                        drawLine(color, from, to, strokeWidth = stroke.width)
                        val angle = kotlin.math.atan2(to.y - from.y, to.x - from.x)
                        val length = maxOf(stroke.width * 4, minOf(image.width, image.height) * .05f)
                        for (offset in listOf(-.5f, .5f)) drawLine(color, to,
                            Offset(to.x - kotlin.math.cos(angle + offset) * length, to.y - kotlin.math.sin(angle + offset) * length), strokeWidth = stroke.width)
                    }
                    MarkupKind.ELLIPSE -> if (mark.filled) drawOval(color, bounds.topLeft, bounds.size) else drawOval(color, bounds.topLeft, bounds.size, style = stroke)
                    MarkupKind.RECTANGLE -> if (mark.filled) drawRect(color, bounds.topLeft, bounds.size) else drawRect(color, bounds.topLeft, bounds.size, style = stroke)
                    MarkupKind.COVER -> drawRect(color, bounds.topLeft, bounds.size)
                    MarkupKind.PIXELATE -> drawRect(primary, bounds.topLeft, bounds.size, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
