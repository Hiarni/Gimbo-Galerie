package de.luma.gallery.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import de.luma.gallery.R
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(UnstableApi::class)
class VideoExporter(private val context: Context) {
    suspend fun export(item: GalleryMedia, recipe: VideoRecipe, progress: (Int?) -> Unit = {}): File {
        var generated: File? = null
        try { return withContext(Dispatchers.Main) {
            requireMessage(recipe.isValid(item.durationMs), R.string.video_clip_min_length)
            val clips = recipe.orderedClips(item)
            clips.forEach { GalleryRepository(context).checkSource(it.source) }
            withContext(Dispatchers.IO) {
                val uris = listOfNotNull(recipe.music?.uri) + recipe.audioTracks.map { it.music.uri } + recipe.overlays.mapNotNull { it.imageUri }
                for (uri in uris) context.contentResolver.openAssetFileDescriptor(android.net.Uri.parse(uri), "r")?.use { } ?: throw java.io.IOException()
            }
            val info = SourceProbe.inspect(context, clips.first().source)
            val composition = VideoCompositionFactory.create(item, recipe, info, context)
            val output = File.createTempFile("luma_export_", ".mp4", context.cacheDir)
            generated = output
            check(output.delete())
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var poll: Runnable? = null
                fun finish() { poll?.let(handler::removeCallbacks) }
                val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(true)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(recipe.bitrate).build()).build())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            finish()
                            if (continuation.isActive) continuation.resume(output) else output.delete()
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            finish(); output.delete()
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    }).build()
                poll = object : Runnable { override fun run() {
                    if (!continuation.isActive) return
                    val holder = ProgressHolder()
                    progress(if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                    handler.postDelayed(this, 300)
                } }
                continuation.invokeOnCancellation { handler.post { finish(); transformer.cancel(); output.delete() } }
                try { transformer.start(composition, output.absolutePath); handler.post(requireNotNull(poll)) }
                catch (error: Throwable) { finish(); output.delete(); if (continuation.isActive) continuation.resumeWithException(error) }
            }
        } } catch (error: Throwable) { generated?.delete(); throw error }
    }
}
