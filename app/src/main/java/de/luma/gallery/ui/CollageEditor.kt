package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.data.CollageEngine
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.conflate

@Composable
fun CollageEditor(initial:List<GalleryMedia>,library:List<GalleryMedia>,background:Long,busy:Boolean,onBack:()->Unit,onExport:(List<GalleryMedia>,CollageSpec,PhotoExportOptions)->Unit) {
    val strings=appStrings();val context=LocalContext.current
    var ids by rememberSaveable {mutableStateOf(initial.map {it.id})}
    var recipe by rememberSaveable {mutableStateOf(CollageEngine.json(CollageSpec(background=background,tiles=initial.map {CollageTile()})))}
    val spec=CollageEngine.read(recipe)
    val available=remember(library,initial) {(library+initial).associateBy {it.id}}
    val photos=ids.mapNotNull {available[it]}
    var active by rememberSaveable {mutableIntStateOf(0)}
    var replace by remember {mutableStateOf(false)}
    var colors by remember {mutableStateOf(false)}
    var exporting by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var preview by remember {mutableStateOf<Bitmap?>(null)}
    val liveSpec by rememberUpdatedState(spec)
    val livePhotos by rememberUpdatedState(photos)
    LaunchedEffect(Unit) {
        snapshotFlow {Pair(liveSpec,livePhotos)}.conflate().collect {(current,sources)->
            if(sources.size==current.tiles.size) try {preview=CollageEngine.render(context,sources,current,1000);error=null}
            catch(e:Exception) {if(e is CancellationException) throw e;error=strings.getString(R.string.image_edit_failed)}
            catch(e:OutOfMemoryError) {error=strings.getString(R.string.image_memory_limit)}
        }
    }
    fun update(value:CollageSpec) {recipe=CollageEngine.json(value)}
    fun tile(value:CollageTile) {update(spec.copy(tiles=spec.tiles.mapIndexed {i,t->if(i==active) value else t}))}
    fun reorder(direction:Int) {
        val other=(active+direction).coerceIn(0,ids.lastIndex)
        if(other!=active) {
            val list=ids.toMutableList();val swap=list[other];list[other]=list[active];list[active]=swap;ids=list
            val tiles=spec.tiles.toMutableList();val old=tiles[other];tiles[other]=tiles[active];tiles[active]=old
            update(spec.copy(tiles=tiles));active=other
        }
    }
    if(replace) PhotoChoiceDialog(library.filter {!it.isVideo && (it.id !in ids || it.id==ids[active])},1,1,{replace=false}) {chosen->
        ids=ids.mapIndexed {i,id->if(i==active) chosen.single().id else id};tile(CollageTile());replace=false
    }
    if(colors) CompactDialog(strings.getString(R.string.collage_background),{colors=false},actions={TextButton(onClick={colors=false}) {Text(strings.getString(R.string.apply))}}) {
        AccentPicker(AppSettings(accent=spec.background or 0xFF000000L),true) {update(spec.copy(background=it.accent))}
    }
    if(exporting) CompactDialog(strings.getString(R.string.collage_save),{exporting=false},actions={TextButton(onClick={exporting=false}) {Text(strings.getString(R.string.cancel))}}) {
        Text(strings.getString(R.string.collage_save_hint),style=MaterialTheme.typography.bodySmall)
        listOf(PhotoFormat.JPEG,PhotoFormat.PNG).forEach {format->
            listOf(1920,2560,4096).forEach {edge->OutlinedButton(onClick={exporting=false;onExport(photos,spec,PhotoExportOptions(format,edge,95,MetadataMode.NONE))},enabled=!busy && photos.size==ids.size,modifier=Modifier.fillMaxWidth()) {Text("${format.label} · ${edge}px")}}
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back))}
            Text(strings.getString(R.string.collage_title),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            Button(onClick={exporting=true},enabled=!busy && preview!=null && photos.size==ids.size) {Text(strings.getString(R.string.save))}
        }
        EditorPanes(Modifier.weight(1f),preview={modifier->
            BoxWithConstraints(modifier.padding(16.dp),contentAlignment=Alignment.Center) {
                val width=minOf(maxWidth,maxHeight*spec.ratio);val height=width/spec.ratio
                Box(Modifier.width(width).height(height).background(Color(spec.background))) {
                    preview?.let {Image(it.asImageBitmap(),strings.getString(R.string.collage_preview),Modifier.fillMaxSize())}
                    val cells=spec.cells()
                    cells.forEachIndexed {index,cell->
                        val density=androidx.compose.ui.platform.LocalDensity.current
                        val tileWidth=with(density) {(width*(cell.right-cell.left)).toPx()}
                        val tileHeight=with(density) {(height*(cell.bottom-cell.top)).toPx()}
                        val current by rememberUpdatedState(spec.tiles[index])
                        val transform=rememberTransformableState {zoom,pan,_->
                            active=index
                            tile(current.copy(zoom=(current.zoom*zoom).coerceIn(1f,4f),panX=(current.panX-pan.x/tileWidth*2).coerceIn(-1f,1f),panY=(current.panY-pan.y/tileHeight*2).coerceIn(-1f,1f)))
                        }
                        Box(Modifier.offset(width*cell.left,height*cell.top).width(width*(cell.right-cell.left)).height(height*(cell.bottom-cell.top))
                            .border(if(active==index) 2.dp else 0.dp,MaterialTheme.colorScheme.primary,RoundedCornerShape(8.dp))
                            .semantics {contentDescription=strings.getString(R.string.collage_photo,index+1);selected=active==index}
                            .clickable {active=index}.transformable(transform))
                    }
                }
            }
        },controls={modifier->Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text(strings.getString(R.string.collage_layout),style=MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(CollageLayout.entries) {layout->FilterChip(spec.layout==layout,{update(spec.copy(layout=layout))},{Text(strings.getString(layout.labelRes))})}}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(listOf("1:1" to 1f,"4:5" to .8f,"16:9" to 16f/9,"9:16" to 9f/16)) {(label,ratio)->FilterChip(spec.ratio==ratio,{update(spec.copy(ratio=ratio))},{Text(label)})}}
            EditorSlider(strings.getString(R.string.collage_gap),spec.gap,0f.. .05f,{update(spec.copy(gap=it))},{})
            EditorSlider(strings.getString(R.string.collage_corners),spec.corner,0f.. .12f,{update(spec.copy(corner=it))},{})
            TextButton(onClick={colors=true}) {Text(strings.getString(R.string.collage_background))}
            Text(strings.getString(R.string.collage_photo,active+1),style=MaterialTheme.typography.titleSmall)
            Text(strings.getString(R.string.collage_gesture_hint),style=MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item {ActionButton(LumaIcons.Rotate90DegreesCw,strings.getString(R.string.rotate_90),{tile(spec.tiles[active].copy(quarterTurns=(spec.tiles[active].quarterTurns+1)%4))})}
                item {ActionButton(LumaIcons.PhotoLibrary,strings.getString(R.string.collage_replace),{replace=true})}
                item {ActionButton(LumaIcons.ArrowBack,strings.getString(R.string.collage_before),{reorder(-1)},active>0)}
                item {ActionButton(LumaIcons.ArrowForward,strings.getString(R.string.collage_after),{reorder(1)},active<ids.lastIndex)}
            }
            EditorSlider(strings.getString(R.string.collage_zoom),spec.tiles[active].zoom,1f..4f,{tile(spec.tiles[active].copy(zoom=it))},{})
            EditorSlider(strings.getString(R.string.collage_horizontal),spec.tiles[active].panX,-1f..1f,{tile(spec.tiles[active].copy(panX=it))},{})
            EditorSlider(strings.getString(R.string.collage_vertical),spec.tiles[active].panY,-1f..1f,{tile(spec.tiles[active].copy(panY=it))},{})
        }})
    }
}
