package de.luma.gallery.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.luma.gallery.R
import de.luma.gallery.data.MediaDetails
import de.luma.gallery.model.GalleryMedia

@Composable
fun MediaDetailsDialog(media: GalleryMedia, onDismiss: () -> Unit) {
    val strings=appStrings();val context=LocalContext.current
    var location by remember(media.uri) { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { location=it }
    var details by remember(media.uri) { mutableStateOf<List<Pair<Int,String>>?>(null) }
    var failed by remember(media.uri) {mutableStateOf(false)}
    LaunchedEffect(media.uri,location) {
        try {details=MediaDetails.read(context,media,location);failed=false}
        catch(e:Exception) {if(e is kotlinx.coroutines.CancellationException) throw e;failed=true}
    }
    CompactDialog(strings.getString(R.string.file_info),onDismiss,actions={TextButton(onClick=onDismiss) {Text(strings.getString(R.string.close))}}) {
        if(failed) Text(strings.getString(R.string.check_access),color=MaterialTheme.colorScheme.error)
        else if(details==null) CircularProgressIndicator()
        val basic=setOf(R.string.info_filename,R.string.album,R.string.info_resolution,R.string.info_bytes,R.string.capture_date)
        val storage=setOf(R.string.info_uri,R.string.info_path,R.string.info_owner,R.string.info_added,R.string.info_modified)
        if(details!=null) {
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                details?.filter {it.first in basic}?.forEach {(label,value)->DetailField(strings.getString(label),value)}
                val metadata=details.orEmpty().filter {it.first !in basic && it.first !in storage}
                key("metadata") {if(metadata.isNotEmpty()) EditorSection(strings.getString(R.string.info_technical),LumaIcons.Info) {
                    metadata.forEach {(label,value)->DetailField(strings.getString(label),value)}
                }}
                key("storage") {EditorSection(strings.getString(R.string.info_storage),LumaIcons.FolderOpen) {
                    details?.filter {it.first in storage}?.forEach {(label,value)->DetailField(strings.getString(label),value)}
                }}
            }
        }
        if(!location) TextButton(onClick={
            if(media.uri.authority=="media" && ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            else location=true
        }) {Text(strings.getString(R.string.info_show_location))}
    }
}

@Composable
private fun DetailField(label:String,value:String) {
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {Text(value,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.fillMaxWidth())}
    }
}
