package de.luma.gallery.ui

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import de.luma.gallery.R
import kotlinx.coroutines.CancellationException

internal inline fun <T> videoPreviewResult(block: () -> T): Result<T> = try { Result.success(block()) }
catch (error: Exception) { if (error is CancellationException) throw error; Result.failure(error) }

internal inline fun videoPreviewOperation(onFailure: (Exception) -> Unit = {}, block: () -> Unit): Boolean {
    val result = videoPreviewResult(block)
    result.exceptionOrNull()?.let {
        Log.e("GimboVideoPreview", "Video preview operation failed", it)
        onFailure(it as Exception)
    }
    return result.isSuccess
}

@Composable
internal fun VideoPreview(uri: Uri, onError: (String) -> Unit, content: @Composable ((Exception) -> Unit) -> Unit) {
    val strings = appStrings()
    val currentError by rememberUpdatedState(onError)
    var attempt by remember(uri) { mutableIntStateOf(0) }
    var failure by remember(uri, attempt) { mutableStateOf<Exception?>(null) }
    LaunchedEffect(failure) {
        failure?.let {
            Log.e("GimboVideoPreview", "Video preview unavailable", it)
            currentError(strings.getString(R.string.video_play_failed))
        }
    }
    if (failure == null) key(uri, attempt) { content { failure = it } }
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(strings.getString(R.string.video_play_failed), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { attempt++ }) { Text(strings.getString(R.string.retry_preview)) }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoPreviewSurface(player: Player, configured: Boolean = true, showControls: Boolean = true,
    onSurfaceTap: (() -> Unit)? = null, controlInsetPx: Int = 0, textureSurface: Boolean = false, onFailure: (Exception) -> Unit) {
    val tap by rememberUpdatedState(onSurfaceTap)
    val failure by rememberUpdatedState(onFailure)
    AndroidView(factory = { context ->
        FrameLayout(context).apply {
            videoPreviewOperation(failure) {
                val surface = if (textureSurface) android.view.LayoutInflater.from(context)
                    .inflate(R.layout.review_video_player, this, false) as PlayerView else object : PlayerView(context) {
                    override fun performClick(): Boolean {
                        val handled = super.performClick()
                        val action = tap
                        if (action != null) { action(); return true }
                        return handled
                    }
                }
                surface.setKeepContentOnPlayerReset(true)
                addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
    }, update = { container ->
        val view = container.getChildAt(0) as? PlayerView
        if (view != null) videoPreviewOperation(failure) {
            val revealControls = configured && showControls && !view.useController
            view.useController = configured && showControls
            view.controllerAutoShow = configured && showControls
            view.player = if (configured) player else null
            val overlay = view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_controller)
            val margins = overlay?.layoutParams as? ViewGroup.MarginLayoutParams
            if (margins != null && margins.bottomMargin != controlInsetPx) {
                margins.bottomMargin = controlInsetPx
                overlay.layoutParams = margins
            }
            if (!showControls || !configured) view.hideController()
            else if (revealControls) view.showController()
        }
    }, modifier = Modifier.fillMaxSize(), onRelease = { container ->
        videoPreviewOperation { (container.getChildAt(0) as? PlayerView)?.player = null }
    })
}
