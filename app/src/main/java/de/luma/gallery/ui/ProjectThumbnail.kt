package de.luma.gallery.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import de.luma.gallery.R
import de.luma.gallery.data.ProjectPreviewData
import de.luma.gallery.model.EditorProject

@Composable
internal fun ProjectThumbnail(project: EditorProject, imageLoader: ImageLoader, revision: Long = 0) {
    val strings = appStrings()
    val context = LocalContext.current
    val data = remember(project.source, project.photo, project.video, revision) { ProjectPreviewData(project).copy(revision = revision) }
    val request = remember(data, context) { ImageRequest.Builder(context).data(data).memoryCacheKey(data.cacheKey()).build() }
    SubcomposeAsyncImage(model = request, imageLoader = imageLoader, contentDescription = strings.getString(if (project.source.isVideo) R.string.project_video_preview else R.string.project_edited_preview),
        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
        loading = { Icon(if (project.source.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,
            strings.getString(R.string.project_preview_loading)) },
        error = { Icon(if (project.source.isVideo) LumaIcons.PlayArrow else LumaIcons.PhotoLibrary,
            strings.getString(R.string.project_preview_unavailable)) }, success = { SubcomposeAsyncImageContent() })
}
