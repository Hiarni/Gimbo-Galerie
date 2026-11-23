package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.data.BatchPreviewRenderer
import de.luma.gallery.localization.userMessage
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate

@Composable
fun BatchPhotoPreview(item: GalleryMedia, recipe: PhotoRecipe, original: Boolean = false) {
    val context = LocalContext.current; val strings = appStrings()
    val renderer = remember(context) { BatchPreviewRenderer(context) }
    var retry by remember(item.uri) { mutableIntStateOf(0) }

    val liveRecipe by rememberUpdatedState(recipe)
    val liveOriginal by rememberUpdatedState(original)
    key(item.uri, item.timestamp, item.size, retry) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var failure by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(Unit) {
            snapshotFlow {Pair(liveRecipe,liveOriginal)}.conflate().collect {(current,showOriginal)->
                try {bitmap=renderer.render(item,current,showOriginal);failure=null}
                catch(e:Exception) {if(e is CancellationException) throw e;failure=e}
                catch(e:OutOfMemoryError) {failure=e}
            }
        }
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), strings.getString(if (original) R.string.batch_preview_original else R.string.batch_preview_edited, item.name),
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                ?: if (failure != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (failure is OutOfMemoryError) strings.getString(R.string.image_memory_limit)
                        else failure!!.userMessage(strings, R.string.image_open_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text(strings.getString(R.string.retry_preview)) }
                } else CircularProgressIndicator(Modifier.semantics { contentDescription = strings.getString(R.string.batch_preview_loading, item.name) })
        }
    }
}
