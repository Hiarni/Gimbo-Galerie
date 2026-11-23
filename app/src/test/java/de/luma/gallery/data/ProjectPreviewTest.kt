package de.luma.gallery.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "de")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectPreviewTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var file: File
    private lateinit var source: GalleryMedia
    @Before fun setup() {

        Dispatchers.setMain(UnconfinedTestDispatcher())
        file = File.createTempFile("preview_", ".png", app.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } finally { bitmap.recycle() }
        source = GalleryMedia("source", Uri.fromFile(file), "Original.png", "Test", 0)
    }
    @After fun cleanup() { file.delete(); Dispatchers.resetMain() }

    private fun photo(color: Long) = PhotoRecipe(quarterTurns = 1, cropBounds = CropBounds(.1f, .1f, .9f, .9f),
        markups = listOf(PhotoMarkup("cover", MarkupKind.COVER, color = color)))

    @Test fun nativePreviewUsesSavedGeometryAndColorsAndPreservesTheSource() = runBlocking {
        val bytes = file.readBytes()
        val red = ProjectPreviewData(source, photo(0xFFFF0000), null)
        val green = red.copy(photo = photo(0xFF00FF00))
        val renderer = ProjectPreviewRenderer(app)
        val first = renderer.render(red); val second = renderer.render(green)
        try {
            assertEquals(48, first.width); assertEquals(64, first.height)
            assertEquals(Color.RED, first.getPixel(24, 32)); assertEquals(Color.GREEN, second.getPixel(24, 32))
            assertEquals(Color.BLUE, first.getPixel(0, 0)); assertArrayEquals(bytes, file.readBytes())
            assertNotEquals(red.cacheKey(), green.cacheKey())
            val project = EditorProject("a", "Name", source, red.photo, null, 0, 0)
            assertEquals(red.cacheKey(), ProjectPreviewData(project.copy(id = "b", name = "Renamed", updatedAt = 10)).cacheKey())
        } finally { first.recycle(); second.recycle() }
    }

    @Test fun actualCoilLoaderKeepsEditedVariantsSeparateInItsMemoryCache() = runBlocking {
        val loader = ImageLoader.Builder(app).components { add(ProjectPreviewFetcher.Factory(app)) }.diskCache(null).interceptorDispatcher(Dispatchers.Unconfined).build()
        try {
            for (color in listOf(0xFFFF0000L, 0xFF00FF00L, 0xFFFF0000L)) {
                val data = ProjectPreviewData(source, photo(color), null)
                val result = withTimeout(15_000) {loader.execute(ImageRequest.Builder(app).data(data).memoryCacheKey(data.cacheKey()).build())}
                assertTrue(result is SuccessResult)
                assertEquals(color.toInt(), (result.drawable as BitmapDrawable).bitmap.getPixel(24, 32))
            }
            assertTrue(loader.memoryCache!!.keys.size >= 2)
        } finally { loader.shutdown() }
    }

    @Test fun missingSourceFailsWithoutChangingTheProject() = runBlocking {
        val data = ProjectPreviewData(source.copy(uri = Uri.fromFile(File(app.cacheDir, "missing-preview-source.png"))), PhotoRecipe(), null)
        val before = data.copy()
        assertTrue(runCatching { ProjectPreviewRenderer(app).render(data) }.isFailure)
        assertEquals(before, data); assertTrue(file.exists())
    }

    @Test fun videoPreviewRequestsTheSavedClipStartAndRejectsInvalidClips() = runBlocking {
        val videoSource = source.copy(isVideo = true, durationMs = 10_000)
        var requested: Long? = null
        val frame = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val renderer = ProjectPreviewRenderer(app) { media, start -> assertEquals(videoSource, media); requested = start; frame }
        val result = renderer.render(ProjectPreviewData(videoSource, null, VideoRecipe(5_000, 8_000, true)))
        try { assertSame(frame, result); assertEquals(5_000L, requested) } finally { result.recycle() }
        requested = null
        assertTrue(runCatching { renderer.render(ProjectPreviewData(videoSource, null, VideoRecipe(5_000, 15_000))) }.isFailure)
        assertNull(requested)
    }

    @Test fun cancellationRecyclesGeneratedVideoFrameAndLeavesTheSourceUntouched() = runBlocking {
        val videoSource = source.copy(isVideo = true, durationMs = 10_000)
        val bytes = file.readBytes(); val child = Job()
        val frame = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        val renderer = ProjectPreviewRenderer(app) { _, _ -> child.cancel(); frame }
        assertTrue(runCatching { withContext(child) { renderer.render(ProjectPreviewData(videoSource, null, VideoRecipe(0, 8_000))) } }.exceptionOrNull() is CancellationException)
        assertTrue(frame.isRecycled); assertArrayEquals(bytes, file.readBytes())
    }
}
