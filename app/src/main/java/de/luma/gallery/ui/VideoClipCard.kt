package de.luma.gallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import de.luma.gallery.model.VideoClip

@Composable
fun VideoClipCard(clip:VideoClip,index:Int,selected:Boolean,onClick:()->Unit) {
    Surface(Modifier.width(132.dp).clickable(onClick=onClick),shape=RoundedCornerShape(16.dp),
        color=MaterialTheme.colorScheme.surfaceContainerHighest,border=if(selected) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
        Column {
            AsyncImage(ImageRequest.Builder(LocalContext.current).data(clip.source.uri).videoFrameMillis(clip.startMs+minOf(500,(clip.endMs-clip.startMs)/2)).build(),null,
                Modifier.fillMaxWidth().height(74.dp),contentScale=ContentScale.Crop)
            Text("${index+1} · ${clip.source.name}",Modifier.padding(horizontal=8.dp,vertical=4.dp),maxLines=1,style=MaterialTheme.typography.labelMedium)
            Text("${durationLabel(clip.endMs-clip.startMs)} · ${clip.speed}×",Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall)
        }
    }
}
