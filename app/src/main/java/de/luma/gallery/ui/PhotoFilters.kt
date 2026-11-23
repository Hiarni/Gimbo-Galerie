package de.luma.gallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import de.luma.gallery.R
import de.luma.gallery.data.PhotoEngine
import de.luma.gallery.model.*
import kotlinx.coroutines.*

@Composable
fun PhotoFilters(source:Bitmap?,draft:PhotoRecipe,onChange:(PhotoRecipe)->Unit,onFinish:()->Unit,onCommit:(PhotoRecipe)->Unit) {
    val strings=appStrings();var selected by remember {mutableStateOf<String?>(null)}
    var base by remember {mutableStateOf(draft)};var strength by remember {mutableFloatStateOf(1f)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        LazyRow(modifier=Modifier.semantics {contentDescription=strings.getString(R.string.filter_previews)},horizontalArrangement=Arrangement.spacedBy(10.dp)) {items(PHOTO_PRESETS,key={it.id}) {preset->
            val thumbnail by produceState<Bitmap?>(null,source,preset.id) {
                if(source!=null) {
                    var generated:Bitmap?=null
                    try { value=withContext(Dispatchers.Default) {
                        val factor=160f/maxOf(source.width,source.height)
                        val small=Bitmap.createScaledBitmap(source,maxOf(1,(source.width*factor).toInt()),maxOf(1,(source.height*factor).toInt()),true)
                        try {val renderContext=currentCoroutineContext();PhotoEngine.render(small,preset.recipe) {renderContext.ensureActive()}.also {generated=it}} finally {if(small!==source) small.recycle()}
                    }}catch(e:CancellationException) {generated?.recycle();throw e}
                    catch(_:Exception) {value=null}
                    catch(_:OutOfMemoryError) {value=null}
                }
            }

            Surface(Modifier.width(88.dp).clickable {base=draft;selected=preset.id;strength=1f;onCommit(base.withPreset(preset.recipe,1f))},
                shape=RoundedCornerShape(16.dp),color=if(selected==preset.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(88.dp),contentAlignment=Alignment.Center) {
                        thumbnail?.let {Image(it.asImageBitmap(),strings.getString(R.string.filter_preview,strings.getString(preset.label)),Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)}
                            ?: Icon(LumaIcons.PhotoLibrary,null)
                    }
                    Text(strings.getString(preset.label),Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium,maxLines=1)
                }
            }
        } }
        selected?.let {id->val preset=PHOTO_PRESETS.first {it.id==id}
            EditorSlider(strings.getString(R.string.filter_strength),strength,0f..1f,{strength=it;onChange(base.withPreset(preset.recipe,it))},onFinish)
        }
    }
}
