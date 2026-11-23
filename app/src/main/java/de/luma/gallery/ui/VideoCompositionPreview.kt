package de.luma.gallery.ui

import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.transformer.CompositionPlayer
import de.luma.gallery.data.SourceProbe
import de.luma.gallery.data.VideoCompositionFactory
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate
import kotlin.coroutines.resume

@Composable
fun VideoCompositionPreview(item: GalleryMedia, recipe: VideoRecipe, playRequest: Int, seekMs: Long = 0, onError: (String) -> Unit) {
    VideoPreview(item.uri, onError) { onFailure ->
        CompositionSession(item, recipe, playRequest, seekMs, onFailure)
    }
}

@androidx.annotation.OptIn(UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class)
@Composable
private fun CompositionSession(item: GalleryMedia, recipe: VideoRecipe, playRequest: Int, seekMs: Long, onFailure: (Exception) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentFailure by rememberUpdatedState(onFailure)
    val liveRecipe by rememberUpdatedState(recipe)
    val liveSeek by rememberUpdatedState(seekMs)
    val livePlay by rememberUpdatedState(playRequest)
    val firstSource = recipe.orderedClips(item).firstOrNull()?.source
    var info by remember(firstSource?.uri) { mutableStateOf<SourceInfo?>(null) }
    LaunchedEffect(firstSource?.uri) {
        try { info = SourceProbe.inspect(context, requireNotNull(firstSource)) }
        catch (error: Exception) { if (error is CancellationException) throw error; currentFailure(error) }
    }
    val dimensions = info
    if (dimensions == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val multipleInputs = recipe.crossfadeMs > 0 && recipe.orderedClips(item).size > 1
    val created = remember(multipleInputs) {
        videoPreviewResult {
            CompositionPlayer.Builder(context).setLooper(Looper.getMainLooper())
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .apply { if (multipleInputs) setVideoGraphFactory(MultipleInputVideoGraph.Factory()) }
                .build()
        }
    }
    val player = created.getOrNull()
    if (player == null) {
        LaunchedEffect(created) { currentFailure(created.exceptionOrNull() as Exception) }
        return
    }
    var configured by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { currentFailure(error) }
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) videoPreviewOperation(currentFailure) { player.pause() }
        }
        player.addListener(listener)
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            videoPreviewOperation { player.removeListener(listener); player.release() }
        }
    }
    LaunchedEffect(player, dimensions) {
        var applied: VideoRecipe? = null
        var lastSeek = liveSeek
        var lastPlay = 0
        snapshotFlow { Triple(liveRecipe, liveSeek, livePlay) }.conflate().collect { (current, seek, play) ->
            if (!current.isValid(item.durationMs)) {
                videoPreviewOperation(currentFailure) { player.pause() }
                if (applied == null) currentFailure(IllegalArgumentException("Invalid video timeline"))
                return@collect
            }
            try {
                val duration = current.outputDuration(item)
                val seeking = applied == null || seek != lastSeek
                val replay = play > lastPlay
                val position = when {
                    replay -> 0L
                    seeking -> seek
                    else -> player.currentPosition
                }.coerceIn(0, (duration - 1).coerceAtLeast(0))
                val playing = (replay || !seeking && player.playWhenReady) && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (current != applied) {
                    withTimeoutOrNull(1000) {
                        suspendCancellableCoroutine<Unit> { waiting ->
                            val rendered = object : Player.Listener {
                                override fun onRenderedFirstFrame() { player.removeListener(this); if (waiting.isActive) waiting.resume(Unit) }
                                override fun onPlayerError(error: PlaybackException) { player.removeListener(this); if (waiting.isActive) waiting.resume(Unit) }
                            }
                            player.addListener(rendered)
                            waiting.invokeOnCancellation { android.os.Handler(player.applicationLooper).post {
                                videoPreviewOperation { player.removeListener(rendered) }
                            } }
                            try {
                                player.setComposition(VideoCompositionFactory.create(item, current, dimensions, context, preview = true), position)
                                player.prepare()
                                applied = current
                                configured = true
                                player.playWhenReady = playing
                            } catch (error: Exception) {
                                player.removeListener(rendered)
                                if (waiting.isActive) waiting.resume(Unit)
                                throw error
                            }
                        }
                    }
                } else if (seeking || replay) {
                    player.seekTo(position)
                    player.playWhenReady = playing
                }
                lastSeek = seek
                lastPlay = play
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                currentFailure(error)
            }
        }
    }
    VideoPreviewSurface(player, configured, onFailure = currentFailure)
}
