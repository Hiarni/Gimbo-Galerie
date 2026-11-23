package de.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import de.luma.gallery.R
import de.luma.gallery.localization.messageError
import de.luma.gallery.localization.requireMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest


data class ProjectPreviewData(val source: GalleryMedia, val photo: PhotoRecipe?, val video: VideoRecipe?, val revision: Long = 0) {
    constructor(project: EditorProject) : this(project.source, project.photo, project.video)
    fun cacheKey(): String {
        val recipe = photo?.let(RecipeJson::photo) ?: video?.let(RecipeJson::video).orEmpty()
        val raw = "${source.uri}\n${source.timestamp}\n${source.size}\n${source.isVideo}\n$revision\n$recipe"
        return "project-preview-v1:" + MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

class ProjectPreviewRenderer(private val context: Context,
    private val videoFrame: (GalleryMedia, Long) -> Bitmap = { source, start ->
        MediaMetadataRetriever().use { reader ->
            reader.setDataSource(context, source.uri)
            reader.getScaledFrameAtTime(start * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
                ?: messageError(R.string.project_preview_unavailable)
        }
    }) {
    suspend fun render(data: ProjectPreviewData): Bitmap {
        var generated: Bitmap? = null
        try {
            return withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                if (!data.source.isVideo) GalleryRepository(context).checkSource(data.source)
                val result = if (data.source.isVideo) {
                    val clip = data.video
                    requireMessage(clip != null && data.photo == null && clip.isValid(data.source.durationMs) &&
                        clip.startMs <= Long.MAX_VALUE / 1000, R.string.invalid_video_clip)
                    val first = clip.orderedClips(data.source).first()
                    GalleryRepository(context).checkSource(first.source)
                    videoFrame(first.source, first.startMs)
                } else {
                    val recipe = data.photo
                    requireMessage(recipe != null && data.video == null, R.string.project_preview_unavailable)
                    val source = PhotoEngine.decode(context, data.source.uri, 512)
                    try { PhotoEngine.render(source, recipe) { job.ensureActive() } } finally { source.recycle() }
                }
                generated = result
                job.ensureActive()
                result
            }
        } catch (error: Throwable) { generated?.recycle(); throw error }
    }
}

class ProjectPreviewFetcher(private val context: Context, private val data: ProjectPreviewData) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = ProjectPreviewRenderer(context).render(data)
        try {
            currentCoroutineContext().ensureActive()
            return DrawableResult(BitmapDrawable(context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
    class Factory(private val context: Context) : Fetcher.Factory<ProjectPreviewData> {
        override fun create(data: ProjectPreviewData, options: Options, imageLoader: ImageLoader): Fetcher = ProjectPreviewFetcher(context, data)
    }
}
