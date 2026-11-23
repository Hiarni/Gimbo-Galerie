package de.luma.gallery.model

import de.luma.gallery.localization.*
import de.luma.gallery.R
import android.net.Uri

data class GalleryMedia(
    val id: String,
    val uri: Uri,
    val name: String,
    val album: String,
    val timestamp: Long,
    val isVideo: Boolean = false,
    val durationMs: Long = 0,
    val size: Long = 0,
    val isDemo: Boolean = false,
    val isImported: Boolean = false,
)

enum class MediaFilter(val labelRes: Int) {
    ALL(R.string.all), PHOTOS(R.string.photos), VIDEOS(R.string.videos), FAVORITES(R.string.favorites), SCREENSHOTS(R.string.screenshots)
}

enum class MediaSort(val labelRes: Int) {
    DATE(R.string.sort_date), NAME(R.string.sort_name), SIZE(R.string.sort_size), DURATION(R.string.sort_duration), TYPE(R.string.sort_type), ALBUM(R.string.album)
}

enum class ThemeMode(val labelRes: Int) {
    SYSTEM(R.string.system_theme), DARK(R.string.dark_theme), LIGHT(R.string.light_theme)
}

val DEFAULT_PHOTO_TOOLS = listOf("LIGHT", "COLOR", "CURVES", "HSL", "MASKS", "DETAILS", "CROP", "TEXT", "MARKUP", "LAYERS", "FILTERS")

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Long = 0xFFB9A7FF,
    val columns: Int = 3,
    val newestFirst: Boolean = true,
    val thumbnailAnimations: Boolean = true,
    val toolOrder: List<String> = DEFAULT_PHOTO_TOOLS,
    val rememberGrid: Boolean = true,
    val trashDays: Int = 30,
    val sort: MediaSort = MediaSort.DATE,
    val groupByDay: Boolean = true,
    val albumOrder: List<String> = emptyList(),
    val albumColumns: Int = 2,
    val autoPlayVideos: Boolean = true,
    val reviewGestures:Boolean = true,
    val reviewLeft:ReviewAction = ReviewAction.REMOVE,
    val reviewRight:ReviewAction = ReviewAction.KEEP,
    val reviewPlayful:Boolean = true,
)


data class PhotoRecipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropRatio: Float? = null,
    val cropBounds: CropBounds? = null,
    val exposure: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val sharpness: Float = 0f,
    val curves: PhotoCurves = PhotoCurves(),
    val texts: List<PhotoText> = emptyList(),
    val markups: List<PhotoMarkup> = emptyList(),
    val layerOrder: List<String> = emptyList(),
    val hsl: PhotoHsl = PhotoHsl(),
    val masks: List<PhotoMask> = emptyList(),
    val perspectiveX: Float = 0f, val perspectiveY: Float = 0f,
) {
    init {
        requireMessage(perspectiveX.isFinite() && perspectiveY.isFinite() && perspectiveX in -.4f.. .4f && perspectiveY in -.4f.. .4f, R.string.invalid_photo_details)
        requireMessage(masks.size <= 8 && masks.map { it.id }.distinct().size == masks.size, R.string.invalid_mask)
        requireMessage(layerOrder.size <= PHOTO_MARKUP_LIMIT + PHOTO_TEXT_LIMIT && layerOrder.distinct().size == layerOrder.size &&
            layerOrder.all { it.length <= 85 && (it.startsWith("mark:") || it.startsWith("text:")) }, R.string.invalid_layers)
        requireMessage(brightness.isFinite() && brightness in -1f..1f && contrast.isFinite() && contrast in 0f..3f &&
            saturation.isFinite() && saturation in 0f..3f && exposure.isFinite() && exposure in -4f..4f &&
            temperature.isFinite() && temperature in -1f..1f && tint.isFinite() && tint in -1f..1f &&
            (cropRatio == null || cropRatio.isFinite() && cropRatio > 0f), R.string.invalid_photo_details)
        requireMessage(highlights.isFinite() && highlights in -1f..1f && shadows.isFinite() && shadows in -1f..1f &&
            sharpness.isFinite() && sharpness in 0f..1f, R.string.invalid_photo_details)
        requireMessage(markups.size <= PHOTO_MARKUP_LIMIT && markups.map { it.id }.distinct().size == markups.size, R.string.invalid_markup)
        requireMessage(texts.size <= PHOTO_TEXT_LIMIT && texts.map { it.id }.distinct().size == texts.size, R.string.invalid_text_objects)
    }
}

data class VideoRecipe(val startMs: Long, val endMs: Long, val mute: Boolean = false,
    val speed: Float = 1f, val volume: Float = 1f, val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val caption: PhotoText? = null, val extraClips: List<VideoClip> = emptyList(), val clipOrder: List<String> = emptyList(),
    val includeSource: Boolean = true, val music: VideoMusic? = null, val fadeInMs: Long = 0, val fadeOutMs: Long = 0,
    val outputHeight: Int = 1080, val bitrate: Int = 6_000_000, val transitionMs: Long = 0, val crossfadeMs: Long = 0,
    val keyframes: List<VideoKeyframe> = emptyList(), val audioTracks: List<VideoAudioTrack> = emptyList(), val overlays: List<VideoOverlay> = emptyList(),
    val quarterTurns:Int=0,val flipHorizontal:Boolean=false,val flipVertical:Boolean=false,
    val cropBounds:CropBounds?=null,val aspectRatio:Float?=null,val fillFrame:Boolean=false,
    val temperature:Float=0f,val tint:Float=0f) {
    init {requireMessage(quarterTurns in 0..3 && (aspectRatio==null || aspectRatio.isFinite() && aspectRatio in .25f..4f) &&
        temperature.isFinite() && temperature in -1f..1f && tint.isFinite() && tint in -1f..1f,R.string.invalid_video_clip)}
    init { requireMessage(crossfadeMs in 0..5000 && keyframes.size <= 100 && keyframes.map { it.timeMs }.distinct().size == keyframes.size &&
        audioTracks.size <= 8 && audioTracks.map { it.id }.distinct().size == audioTracks.size && overlays.size <= 16 && overlays.map { it.id }.distinct().size == overlays.size, R.string.invalid_video_clip) }
    init { requireMessage(speed.isFinite() && speed in .25f..4f && volume.isFinite() && volume in 0f..2f &&
        listOf(brightness, contrast, saturation).all { it.isFinite() && it in -1f..1f } &&
        extraClips.size <= 19 && extraClips.map { it.id }.distinct().size == extraClips.size && extraClips.none { it.id == "primary" } &&
        clipOrder.size <= 20 && clipOrder.distinct().size == clipOrder.size && clipOrder.all { it.length <= 80 } &&
        fadeInMs in 0..10_000 && fadeOutMs in 0..10_000 && transitionMs in 0..2000 && outputHeight in listOf(720, 1080, 1440, 2160) &&
        bitrate in listOf(3_000_000, 6_000_000, 12_000_000, 24_000_000), R.string.invalid_video_clip) }
    fun isValid(durationMs: Long) = (!includeSource || durationMs in 1..604_800_000 && startMs >= 0 && endMs <= durationMs && endMs - startMs >= 500) &&
        (includeSource || extraClips.isNotEmpty()) && extraClips.all { it.valid() } &&
        (if (includeSource) (endMs.toDouble() - startMs) / speed else 0.0) + extraClips.sumOf { (it.endMs.toDouble() - it.startMs) / it.speed } <= 86_400_000.0
}

data class History<T>(val past: List<T> = emptyList(), val present: T, val future: List<T> = emptyList()) {
    fun commit(value: T): History<T> = if (value == present) this
        else History((past + present).takeLast(50), value)
    fun undo(): History<T> = if (past.isEmpty()) this
        else History(past.dropLast(1), past.last(), listOf(present) + future)
    fun redo(): History<T> = if (future.isEmpty()) this
        else History((past + present).takeLast(50), future.first(), future.drop(1))
}
