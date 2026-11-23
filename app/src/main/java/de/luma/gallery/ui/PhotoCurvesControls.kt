package de.luma.gallery.ui

import de.luma.gallery.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.*
import kotlin.math.roundToInt

@Composable
fun PhotoCurvesControls(curves: PhotoCurves, onChange: (PhotoCurves) -> Unit, onFinish: () -> Unit,
    onReset: (PhotoCurves) -> Unit) {
    val strings = appStrings()
    var channel by rememberSaveable { mutableStateOf(CurveChannel.RGB) }
    var selected by rememberSaveable { mutableIntStateOf(2) }
    val curve = curves.curve(channel)
    val current by rememberUpdatedState(curve)
    val change by rememberUpdatedState<(ToneCurve) -> Unit> { onChange(curves.withCurve(channel, it)) }
    val finish by rememberUpdatedState(onFinish)
    val sampler = remember(curve) { ToneCurveSampler(curve) }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val color = when (channel) {
        CurveChannel.RGB -> primary; CurveChannel.RED -> Color(0xFFE36E70)
        CurveChannel.GREEN -> Color(0xFF50AB72); CurveChannel.BLUE -> Color(0xFF6D9FEA)
    }
    val labels = listOf(strings.getString(R.string.black), strings.getString(R.string.shadows), strings.getString(R.string.midtones), strings.getString(R.string.highlights), strings.getString(R.string.white))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(CurveChannel.entries) { _, option -> FilterChip(channel == option, { channel = option }, { Text(strings.getString(option.labelRes)) }) }
    }
    Text(strings.getString(R.string.curves_hint),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = strings.getString(R.string.curve_description, strings.getString(channel.labelRes)) }
        .pointerInput(channel) {
            var gesture = current
            var point = 2
            var output = .5f
            detectDragGestures(onDragStart = { position ->
                gesture = current
                val inset = 12.dp.toPx()
                point = (((position.x - inset) / (size.width - 2 * inset)) * 4).roundToInt().coerceIn(0, 4)
                selected = point
                output = gesture.values()[point]
            }, onDragEnd = { finish() }, onDragCancel = { finish() }) { event, delta ->
                val height = size.height - 24.dp.toPx()
                if (height > 0) {
                    event.consume()
                    output = (output - delta.y / height).coerceIn(0f, 1f)
                    gesture = gesture.withPoint(point, output)
                    change(gesture)
                }
            }
        }) {
        val inset = 12.dp.toPx()
        val width = size.width - 2 * inset; val height = size.height - 2 * inset
        fun position(x: Float, y: Float) = Offset(inset + x * width, inset + (1 - y) * height)
        drawRect(surface)
        for (index in 0..4) {
            val level = index / 4f
            drawLine(grid, position(level, 0f), position(level, 1f))
            drawLine(grid, position(0f, level), position(1f, level))
        }
        drawLine(grid, position(0f, 0f), position(1f, 1f), 1.dp.toPx())
        val path = Path().apply {
            for (index in 0..255) {
                val input = index / 255f; val at = position(input, sampler.value(input))
                if (index == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        curve.values().forEachIndexed { index, value ->
            val at = position(index / 4f, value)
            drawCircle(surface, if (index == selected) 7.dp.toPx() else 5.dp.toPx(), at)
            drawCircle(color, if (index == selected) 7.dp.toPx() else 5.dp.toPx(), at, style = Stroke(2.dp.toPx()))
        }
    }
    LazyRow(Modifier.semantics { contentDescription = strings.getString(R.string.curve_points) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(labels) { index, label -> FilterChip(selected == index, { selected = index }, { Text(label) }) }
    }
    EditorSlider(strings.getString(R.string.curve_slider_label, strings.getString(channel.labelRes), labels[selected]), curve.values()[selected], 0f..1f,
        { change(curve.withPoint(selected, it)) }, onFinish)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onReset(curves.withCurve(channel, ToneCurve(shadows = .18f, highlights = .82f))) }) { Text(strings.getString(R.string.contrast_curve)) }
        OutlinedButton(onClick = { onReset(curves.withCurve(channel, ToneCurve(shadows = .35f, midtones = .65f, highlights = .85f))) }) { Text(strings.getString(R.string.brighten_curve)) }
    }
    TextButton(onClick = { onReset(curves.withCurve(channel, ToneCurve())) }) { Text(strings.getString(R.string.reset_channel)) }
    TextButton(onClick = { onReset(PhotoCurves()) }) { Text(strings.getString(R.string.reset_curves)) }
}
