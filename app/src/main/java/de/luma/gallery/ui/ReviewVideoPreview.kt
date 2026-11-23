package de.luma.gallery.ui

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import de.luma.gallery.R
import de.luma.gallery.model.GalleryMedia
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(UnstableApi::class)
internal val LocalReviewVideoPlayerFactory = staticCompositionLocalOf<(Context) -> Player> {
    { context -> ExoPlayer.Builder(context).setLooper(Looper.getMainLooper())
        .setAudioAttributes(AudioAttributes.DEFAULT, false).setHandleAudioBecomingNoisy(true).build() }
}

@Composable
internal fun ReviewVideoPreview(item: GalleryMedia, autoPlay: Boolean, enabled: Boolean, muted: Boolean, onMuted: (Boolean) -> Unit) {
    VideoPreview(item.uri, {}) { onFailure ->
        ReviewVideoSession(item, autoPlay, enabled, muted, onMuted, onFailure)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ReviewVideoSession(item: GalleryMedia, autoPlay: Boolean, enabled: Boolean, muted: Boolean,
    onMuted: (Boolean) -> Unit, onFailure: (Exception) -> Unit) {
    val strings = appStrings()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val factory = LocalReviewVideoPlayerFactory.current
    val currentFailure by rememberUpdatedState(onFailure)
    val created = remember(item.uri, factory) { videoPreviewResult { factory(context) } }
    val player = created.getOrNull()
    if (player == null) {
        LaunchedEffect(created) { currentFailure(created.exceptionOrNull() as Exception) }
        return
    }
    var requestedPlaying by remember { mutableStateOf(autoPlay) }
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var position by remember { mutableLongStateOf(0) }
    var duration by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(0)) }
    var buffering by remember { mutableStateOf(false) }
    val active = enabled && foreground
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { currentFailure(error) }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == Player.STATE_BUFFERING }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason in listOf(Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) requestedPlaying = false
            }
        }
        val lifecycle = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> foreground = true
                Lifecycle.Event.ON_PAUSE -> {
                    foreground = false
                    requestedPlaying = false
                    videoPreviewOperation(currentFailure) { player.pause() }
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            owner.lifecycle.removeObserver(lifecycle)
            videoPreviewOperation { try { player.removeListener(listener) } finally { player.release() } }
        }
    }
    LaunchedEffect(player) {
        videoPreviewOperation(currentFailure) {
            player.repeatMode = Player.REPEAT_MODE_ONE
            player.setAudioAttributes(AudioAttributes.DEFAULT, !muted)
            player.volume = if (muted) 0f else 1f
            player.setMediaItem(MediaItem.fromUri(item.uri))
            player.prepare()
        }
    }
    LaunchedEffect(player, active, requestedPlaying) {
        videoPreviewOperation(currentFailure) { player.playWhenReady = active && requestedPlaying }
    }
    LaunchedEffect(player, muted) {
        videoPreviewOperation(currentFailure) {
            player.setAudioAttributes(AudioAttributes.DEFAULT, !muted)
            player.volume = if (muted) 0f else 1f
        }
    }
    LaunchedEffect(player, active) {
        if (active) while (isActive) {
            if (!videoPreviewOperation(currentFailure) {
                position = player.currentPosition.coerceAtLeast(0)
                player.duration.takeIf { it > 0 }?.let { duration = it }
            }) break
            delay(250)
        }
    }
    Box(Modifier.fillMaxSize().testTag("review_video")) {
        VideoPreviewSurface(player, showControls = false, textureSurface = true, onFailure = currentFailure)
        if (buffering && requestedPlaying && active) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .7f)))))
        Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).widthIn(max = 340.dp).fillMaxWidth(),
            shape = CircleShape, color = Color.Black.copy(alpha = .68f), contentColor = Color.White) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { requestedPlaying = !requestedPlaying }, enabled = enabled,
                        modifier = Modifier.testTag("review_video_toggle")) {
                        Icon(if (requestedPlaying) LumaIcons.Stop else LumaIcons.PlayArrow,
                            strings.getString(if (requestedPlaying) R.string.video_pause else R.string.video_play))
                    }
                    Text("${durationLabel(position)} / ${durationLabel(duration)}", Modifier.weight(1f).padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onMuted(!muted) }, enabled = enabled,
                        modifier = Modifier.testTag("review_video_sound")) {
                        Icon(if (muted) LumaIcons.VolumeOff else LumaIcons.Audio,
                            strings.getString(if (muted) R.string.video_sound_on else R.string.video_sound_off))
                    }
                }
                LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(2.dp).clearAndSetSemantics {},
                    color = Color.White.copy(alpha = .85f), trackColor = Color.White.copy(alpha = .2f))
            }
        }
    }
}
