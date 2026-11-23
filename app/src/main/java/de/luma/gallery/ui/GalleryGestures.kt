package de.luma.gallery.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ImmersiveMedia(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(activity, enabled) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (enabled) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

fun rangeSelection(base: Set<String>, ids: List<String>, anchor: String, end: String, select: Boolean): Set<String> {
    val a = ids.indexOf(anchor); val b = ids.indexOf(end)
    if (a < 0 || b < 0) return base
    val range = ids.subList(minOf(a,b), maxOf(a,b)+1).toSet()
    return if (select) base + range else base - range
}

@Composable
fun galleryGridGestures(grid: LazyGridState, media: List<GalleryMedia>, columns: Int, selection: Set<String>,
    onColumns: (Int) -> Unit, onSelection: (Set<String>) -> Unit, scope: CoroutineScope): Modifier {
    return selectableGridGestures(grid, media.map { it.id }, columns, selection, onColumns, onSelection, scope)
}

@Composable
fun selectableGridGestures(grid: LazyGridState, ids: List<String>, columns: Int, selection: Set<String>,
    onColumns: (Int) -> Unit, onSelection: (Set<String>) -> Unit, scope: CoroutineScope, maximumColumns: Int = 8): Modifier {
    val currentColumns by rememberUpdatedState(columns)
    val selected by rememberUpdatedState(selection)
    val resize by rememberUpdatedState(onColumns)
    val setSelection by rememberUpdatedState(onSelection)
    val idSet = remember(ids) {ids.toSet()}
    fun at(point: Offset): String? = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        it.key is String && it.key in idSet && point.x >= it.offset.x && point.x < it.offset.x + it.size.width &&
            point.y >= it.offset.y && point.y < it.offset.y + it.size.height
    }?.key as? String
    var scroll by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { scroll?.cancel() } }
    return Modifier.pointerInput(ids, grid) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val base = selected
            val anchor = at(down.position)
            var point = down.position
            var selecting = false
            var finished = false
            var scale = 1f
            val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val first = event.changes.firstOrNull {it.id == down.id}
                    if (event.changes.none {it.pressed}) {finished = true; break}
                    if (event.changes.count {it.pressed} > 1 || first == null ||
                        (first.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                true
            }
            if (early == null && anchor != null) {
                selecting = true
                setSelection(rangeSelection(base, ids, anchor, anchor, anchor !in base))
            }
            try {
                while (!finished) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count {it.pressed} >= 2) {
                        selecting = false; scroll?.cancel()
                        scale *= event.calculateZoom()
                        if (scale > 1.22f || scale < .82f) {
                            val next = (currentColumns + if (scale > 1f) -1 else 1).coerceIn(2,maximumColumns)
                            if (next != currentColumns) resize(next)
                            scale = 1f
                        }
                        event.changes.forEach {it.consume()}
                    } else if (selecting && anchor != null) {
                        point = event.changes.firstOrNull {it.id == down.id}?.position ?: point
                        at(point)?.let {setSelection(rangeSelection(base,ids,anchor,it,anchor !in base))}
                        event.changes.forEach {it.consume()}
                        val height = grid.layoutInfo.viewportSize.height
                        val speed = when {point.y < 80 -> -18f;point.y > height-80 -> 18f;else -> 0f}
                        scroll?.cancel()
                        if (speed != 0f) scroll = scope.launch {
                            while (true) {
                                grid.scrollBy(speed)
                                at(point)?.let {setSelection(rangeSelection(base,ids,anchor,it,anchor !in base))}
                                delay(16)
                            }
                        }
                    }
                    finished = event.changes.none {it.pressed}
                }
            } finally {scroll?.cancel();scroll = null}
        }
    }
}
