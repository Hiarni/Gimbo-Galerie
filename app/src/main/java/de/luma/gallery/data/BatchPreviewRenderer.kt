package de.luma.gallery.data

import android.content.Context
import android.graphics.Bitmap
import de.luma.gallery.model.GalleryMedia
import de.luma.gallery.model.PhotoRecipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext


class BatchPreviewRenderer(private val context: Context) {
    suspend fun render(item: GalleryMedia, recipe: PhotoRecipe, original: Boolean): Bitmap {
        var result: Bitmap? = null
        try {
            return withContext(Dispatchers.Default) {
                val job = currentCoroutineContext()
                val source = PhotoEngine.decode(context, item.uri, 512)
                result = if (original) source else {
                    try { PhotoEngine.render(source, recipe) { job.ensureActive() } }
                    finally { source.recycle() }
                }
                job.ensureActive()
                requireNotNull(result)
            }
        } catch (failure: Throwable) { result?.recycle(); throw failure }
    }
}
