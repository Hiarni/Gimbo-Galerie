package de.luma.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.GalleryState
import de.luma.gallery.R
import de.luma.gallery.model.GalleryMedia
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaViewer(item: GalleryMedia, state: GalleryState, onBack: () -> Unit, onEdit: () -> Unit,
    onFavorite: () -> Unit, onMove: (Int) -> Unit, onTrash: () -> Unit, onAlbum: () -> Unit, canPrevious: Boolean, canNext: Boolean,
    onArchive: () -> Unit = {}, onRemoveImport:(()->Unit)?=null) {
    val strings = appStrings()
    val context = LocalContext.current
    var info by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var dockHeight by remember {mutableIntStateOf(0)}
    val density=LocalDensity.current
    val playerInset=dockHeight+with(density) {24.dp.roundToPx()}+WindowInsets.safeDrawing.getBottom(density)
    var more by remember(item.id) {mutableStateOf(false)}
    var videoError by remember(item.id) { mutableStateOf<String?>(null) }
    var zoom by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    val leave by rememberUpdatedState(onBack)
    val move by rememberUpdatedState(onMove)
    val previous by rememberUpdatedState(canPrevious)
    val next by rememberUpdatedState(canNext)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().semantics {customActions=listOf(
            CustomAccessibilityAction(strings.getString(R.string.previous_media)) {if(previous) {move(-1);true} else false},
            CustomAccessibilityAction(strings.getString(R.string.next_media)) {if(next) {move(1);true} else false})
        }.pointerInput(item.id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val nativeControls=item.isVideo && controls
                var drag = Offset.Zero; var transformed = false; var exiting = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pan = event.calculatePan()
                    val factor = event.calculateZoom()
                    if(nativeControls) continue
                    if (event.changes.count { it.pressed } > 1 || zoom > 1.01f) {
                        transformed = true
                        val scaled = zoom * factor
                        if (!item.isVideo) exiting = scaled < .72f
                        zoom = scaled.coerceIn(.65f,6f)
                        offset = if (zoom <= 1.01f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-size.width * (zoom-1)/2, size.width * (zoom-1)/2),
                            (offset.y + pan.y).coerceIn(-size.height * (zoom-1)/2, size.height * (zoom-1)/2))
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else {
                        drag += pan
                        if (abs(drag.x) > viewConfiguration.touchSlop && abs(drag.x) > abs(drag.y))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if(exiting) leave() else zoom=zoom.coerceAtLeast(1f)
                if (!exiting && !transformed && abs(drag.x) > size.width * .15f && abs(drag.x) > abs(drag.y)) {
                    if (drag.x < 0 && next) move(1) else if (drag.x > 0 && previous) move(-1)
                }
            }
        }.pointerInput(item.id) { detectTapGestures(onTap = { controls = !controls }, onDoubleTap = {
            if(!item.isVideo) {zoom = if (zoom > 1.01f) 1f else 2.5f; offset = Offset.Zero}
        }) }, contentAlignment = Alignment.Center) {
            if (item.isVideo) {
                if (videoError == null) key(item.id) { VideoPlayer(item,onError={videoError=it},autoPlay=state.settingsReady && state.settings.autoPlayVideos,showControls=controls,onSurfaceTap={controls=!controls},controlInsetPx=if(controls) playerInset else 0) }
                else Text(videoError!!, Modifier.padding(24.dp), color = Color.White)
            } else AsyncImage(coil.request.ImageRequest.Builder(context).data(item.uri).memoryCacheKey("${item.uri}:viewer:${state.libraryRevision}:${item.size}").build(), item.name, Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y
            }, contentScale = ContentScale.Fit)
        }
        if (controls) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(136.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.8f),Color.Transparent))))
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal))
                .padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back),tint=Color.White)}
                Column(Modifier.weight(1f).padding(horizontal=8.dp)) {
                    Text(item.name,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(item.album,color=Color.White.copy(alpha=.75f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick=onFavorite,modifier=Modifier.semantics {
                    selected=item.id in state.favorites
                    stateDescription=strings.getString(if(item.id in state.favorites) R.string.favorite_is_saved else R.string.favorite_not_saved)
                }) {Icon(if(item.id in state.favorites) LumaIcons.Favorite else LumaIcons.FavoriteBorder,strings.getString(R.string.favorite),tint=if(item.id in state.favorites) Color(readableAccent(state.settings.accent.toInt(),0xFF202027.toInt())) else Color.White)}
                IconButton(onClick={info=true}) {Icon(LumaIcons.Info,strings.getString(R.string.file_info),tint=Color.White)}
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom+WindowInsetsSides.Horizontal))
                .padding(horizontal=12.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                val dockColor=Color(0xFF202027)
                Surface(Modifier.widthIn(max=600.dp).fillMaxWidth().testTag("viewer_actions").onSizeChanged {dockHeight=it.height},shape=RoundedCornerShape(32.dp),
                    color=dockColor,contentColor=Color.White) {
                    BoxWithConstraints {
                    val horizontal=LocalDensity.current.fontScale>1.35f && maxWidth<400.dp
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=6.dp),maxItemsInEachRow=if(horizontal) 2 else 4,
                        horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        val referenceOnly=onRemoveImport!=null && !de.luma.gallery.model.canManageMedia(item)
                        ViewerAction(if(referenceOnly) LumaIcons.Close else LumaIcons.DeleteOutline,
                            strings.getString(if(referenceOnly) R.string.remove_import_references else R.string.delete),Modifier.weight(1f),
                            {if(referenceOnly) onRemoveImport?.invoke() else onTrash()},horizontal=horizontal,enabled=state.pendingMediaAction==null,contentColor=Color(0xFFFFB4AB))
                        ViewerAction(LumaIcons.Tune,strings.getString(R.string.edit),Modifier.weight(1f),onEdit,horizontal=horizontal)
                        ViewerAction(LumaIcons.Share,strings.getString(R.string.share),Modifier.weight(1f),{share(context,listOf(item.uri))},horizontal=horizontal)
                        Box(Modifier.weight(1f)) {
                            ViewerAction(LumaIcons.More,strings.getString(R.string.more_actions),Modifier.fillMaxWidth(),{more=true},horizontal=horizontal)
                            DropdownMenu(more,{more=false},shape=RoundedCornerShape(20.dp),containerColor=MaterialTheme.colorScheme.surfaceContainerHigh) {
                                DropdownMenuItem(text={Text(strings.getString(R.string.album))},leadingIcon={Icon(LumaIcons.FolderOpen,null)},
                                    enabled=!state.savingAlbum,onClick={more=false;onAlbum()})
                                DropdownMenuItem(text={Text(strings.getString(if(item.id in state.archived) R.string.unarchive else R.string.library_archive))},
                                    leadingIcon={Icon(LumaIcons.Archive,null)},onClick={more=false;onArchive()})
                                HorizontalDivider(Modifier.padding(horizontal=12.dp,vertical=4.dp))
                                if(onRemoveImport!=null && !referenceOnly) DropdownMenuItem(text={Text(strings.getString(R.string.remove_import_references))},
                                    leadingIcon={Icon(LumaIcons.Close,null)},onClick={more=false;onRemoveImport()})

                            }
                        }
                    }
                    }
                }
            }
        }
    }
    if (info) MediaDetailsDialog(item) { info = false }
}

@Composable
private fun ViewerAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,modifier:Modifier,onClick:()->Unit,
    selected:Boolean?=null,selectedColor:Color=Color.White,horizontal:Boolean=false,enabled:Boolean=true,contentColor:Color=Color.White) {
    val strings=appStrings()
    TextButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=if(horizontal) 52.dp else 64.dp).semantics {
        selected?.let {this.selected=it;stateDescription=strings.getString(if(it) R.string.favorite_is_saved else R.string.favorite_not_saved)}
    },colors=ButtonDefaults.textButtonColors(contentColor=if(selected==true) selectedColor else contentColor),contentPadding=PaddingValues(horizontal=2.dp,vertical=6.dp)) {
        if(horizontal) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=RoundedCornerShape(50),color=Color.White.copy(alpha=.07f)) {Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(22.dp))}}
            Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
        } else Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Surface(shape=RoundedCornerShape(50),color=if(selected==true) selectedColor.copy(alpha=.16f) else Color.White.copy(alpha=.07f)) {Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {Icon(icon,null,Modifier.size(23.dp))}}
            Text(label,style=MaterialTheme.typography.labelMedium,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
        }
    }
}
