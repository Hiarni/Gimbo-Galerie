package de.luma.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.luma.gallery.R
import de.luma.gallery.data.MoodAnalysis
import de.luma.gallery.model.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CreativeScreen(media:List<GalleryMedia>,onBack:()->Unit,onCollage:(List<GalleryMedia>,Long)->Unit,onCollection:(List<GalleryMedia>,String)->Unit) {
    CreativeScreen(media,onBack,onCollage,de.luma.gallery.data.AnalysisSnapshot(),{},{},{},onCollection)
}

@Composable
fun CreativeScreen(media:List<GalleryMedia>,onBack:()->Unit,onCollage:(List<GalleryMedia>,Long)->Unit,analysis:de.luma.gallery.data.AnalysisSnapshot=de.luma.gallery.data.AnalysisSnapshot(),
    onAnalyze:()->Unit={},onPause:()->Unit={},onResume:()->Unit={},onCollection:(List<GalleryMedia>,String)->Unit) {
    val strings=appStrings()
    var pick by remember {mutableStateOf(false)}
    var selectedMood by remember {mutableStateOf<PhotoMood?>(null)}
    val scanning=analysis.task?.active==true
    val count=analysis.task?.done?:0
    val total=analysis.task?.total?:0
    val moods=analysis.moods
    val memories=remember(media,LocalDate.now()) {mediaMemories(media)}
    if(pick) PhotoChoiceDialog(media.filter {!it.isVideo},2,9,{pick=false}) {photos->pick=false;onCollage(photos,0xFFF8F7FA)}
    selectedMood?.let {mood->PhotoChoiceDialog(moods.orEmpty().filter {it.mood==mood}.sortedByDescending {it.score}.map {it.media},2,9,{selectedMood=null}) {photos->selectedMood=null;onCollage(photos,mood.background)}}
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) {Icon(LumaIcons.ArrowBack,strings.getString(R.string.back))}
            Text(strings.getString(R.string.creative_title),style=MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Button(onClick={pick=true},enabled=media.count {!it.isVideo}>=2,modifier=Modifier.fillMaxWidth()) {Icon(LumaIcons.Collage,null);Spacer(Modifier.width(8.dp));Text(strings.getString(R.string.collage_create))}}
            item {Text(strings.getString(R.string.memories_title),style=MaterialTheme.typography.titleLarge);Text(strings.getString(R.string.memories_hint),style=MaterialTheme.typography.bodySmall)}
            if(memories.isEmpty()) item {Text(strings.getString(R.string.memories_empty),style=MaterialTheme.typography.bodyMedium)}
            items(memories,key={it.key}) {memory->
                val month=LocalDate.of(memory.year,memory.month,1).format(DateTimeFormatter.ofPattern("LLLL yyyy",strings.configuration.locales[0]))
                val title=if(memory.labelRes==R.string.memory_on_this_day) strings.getString(memory.labelRes,memory.year) else strings.getString(memory.labelRes,month)
                CreativeCard(title,memory.media,{onCollection(memory.media,title)}) {
                    val photos=memory.media.filter {!it.isVideo}
                    if(photos.size>=2) TextButton(onClick={onCollage(photos.take(9),0xFFF8F7FA)}) {Text(strings.getString(R.string.collage_create))}
                }
            }
            item {Text(strings.getString(R.string.moods_title),style=MaterialTheme.typography.titleLarge);Text(strings.getString(R.string.moods_hint),style=MaterialTheme.typography.bodySmall)}
            item {
                OutlinedButton(onClick=onAnalyze,enabled=!scanning && media.any {!it.isVideo},modifier=Modifier.fillMaxWidth()) {Text(strings.getString(if(moods==null) R.string.moods_analyze else R.string.moods_refresh))}
                if(scanning) {
                    LinearProgressIndicator(progress={if(total==0) 0f else count.toFloat()/total},modifier=Modifier.fillMaxWidth())
                    Text(strings.getString(R.string.review_progress,count,total))
                    Text(strings.getString(R.string.background_continues),style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick=onPause) {Text(strings.getString(R.string.background_pause))}
                }
                if(analysis.task?.status in listOf(de.luma.gallery.data.AnalysisStatus.PAUSED,de.luma.gallery.data.AnalysisStatus.FAILED)) OutlinedButton(onClick=onResume) {Text(strings.getString(R.string.background_resume))}
                analysis.task?.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }
            if(moods!=null && PhotoMood.entries.none {mood->moods.orEmpty().count {it.mood==mood}>=2}) item {Text(strings.getString(R.string.moods_empty))}
            items(PhotoMood.entries.filter {mood->moods.orEmpty().count {it.mood==mood}>=2}) {mood->
                val photos=moods.orEmpty().filter {it.mood==mood}.sortedByDescending {it.score}.map {it.media}
                CreativeCard(strings.getString(mood.labelRes),photos,{onCollection(photos,strings.getString(mood.labelRes))}) {
                    TextButton(onClick={selectedMood=mood}) {Text(strings.getString(R.string.moodboard_create))}
                }
            }
        }
    }
}

@Composable
private fun CreativeCard(title:String,media:List<GalleryMedia>,onOpen:()->Unit,actions:@Composable ()->Unit) {
    val strings=appStrings()
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().height(140.dp).clickable(onClick=onOpen),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                media.take(3).forEach {AsyncImage(it.uri,it.name,Modifier.weight(1f).fillMaxHeight(),contentScale=ContentScale.Crop)}
            }
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick=onOpen)) {Text(title,style=MaterialTheme.typography.titleMedium);Text(strings.getQuantityString(R.plurals.media_count,media.size,media.size),style=MaterialTheme.typography.bodySmall)}
                actions()
            }
        }
    }
}

@Composable
fun PhotoChoiceDialog(media:List<GalleryMedia>,minimum:Int,maximum:Int,onDismiss:()->Unit,onChoose:(List<GalleryMedia>)->Unit) {
    MediaChoiceDialog(media,minimum,maximum,appStrings().getString(R.string.collage_choose),onDismiss,hintRes=R.string.collage_pick_hint,onChoose=onChoose)
}

@Composable
internal fun MediaChoiceDialog(media:List<GalleryMedia>,minimum:Int,maximum:Int,title:String,onDismiss:()->Unit,hintRes:Int=R.string.media_choice_hint,onChoose:(List<GalleryMedia>)->Unit) {
    val strings=appStrings()
    var selected by rememberSaveable {mutableStateOf(emptyList<String>())}
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().heightIn(max=620.dp).padding(16.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge)
                Text(strings.getString(hintRes,minimum,maximum,selected.size),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={selected=if(selected.isNotEmpty()) emptyList() else media.take(maximum).map {it.id}}) {
                    Text(strings.getString(if(selected.isNotEmpty()) R.string.clear_selection else if(media.size<=maximum) R.string.select_all else R.string.media_choice_batch_select,minOf(media.size,maximum)))
                }
                LazyVerticalGrid(GridCells.Fixed(3),Modifier.weight(1f,false).heightIn(min=140.dp),contentPadding=PaddingValues(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    items(media,key={it.id}) {item->Box(Modifier.aspectRatio(1f).clickable {selected=if(item.id in selected) selected-item.id else if(selected.size<maximum) selected+item.id else selected}.semantics {this.selected=item.id in selected}) {
                        AsyncImage(item.uri,item.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        if(item.id in selected) SelectionIndicator(androidx.compose.ui.state.ToggleableState.On,Modifier.align(Alignment.TopEnd).padding(8.dp),overMedia=true,number=selected.indexOf(item.id)+1)
                    }}
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) {Text(strings.getString(R.string.cancel))}
                    Button(onClick={onChoose(selected.mapNotNull {id->media.find {it.id==id}})},enabled=selected.size in minimum..maximum) {Text(strings.getString(R.string.continue_action))}
                }
            }
        }
    }
}
