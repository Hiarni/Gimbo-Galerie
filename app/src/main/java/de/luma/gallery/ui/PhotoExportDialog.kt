package de.luma.gallery.ui

import de.luma.gallery.R
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.luma.gallery.model.MetadataMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.luma.gallery.model.PhotoExportOptions
import de.luma.gallery.model.PhotoFormat
import kotlin.math.roundToInt

@Composable
fun PhotoExportDialog(initial: PhotoExportOptions, onDismiss: () -> Unit, onExport: (PhotoExportOptions) -> Unit, extraHint: String? = null, needsMediaLocation: Boolean = false, overwriteItem:de.luma.gallery.model.GalleryMedia?=null,onOverwrite:((PhotoExportOptions)->Unit)?=null) {
    val strings = appStrings()
    var options by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    var locationDenied by remember { mutableStateOf(false) }
    var requestingLocation by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestingLocation = false
        if (granted) onExport(options) else locationDenied = true
    }
    if (locationDenied) {
        AlertDialog(onDismissRequest = { locationDenied = false }, title = { Text(strings.getString(R.string.metadata_title)) },
            text = { Text(strings.getString(R.string.metadata_permission_required)) },
            confirmButton = { TextButton(onClick = { onExport(options.copy(metadata = MetadataMode.CAMERA)) }) { Text(strings.getString(R.string.export_without_location)) } },
            dismissButton = { TextButton(onClick = { locationDenied = false }) { Text(strings.getString(R.string.cancel)) } })
        return
    }
    CompactDialog(strings.getString(R.string.export),onDismiss,actions={
 TextButton(enabled = !requestingLocation, onClick = {
        if (options.format == PhotoFormat.PNG) onExport(options.copy(metadata = MetadataMode.NONE))
        else if (needsMediaLocation && options.metadata == MetadataMode.CAMERA_AND_LOCATION &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestingLocation = true; locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else onExport(options)
    }) { Text(strings.getString(R.string.save_copy)) }

            if(overwriteItem!=null && onOverwrite!=null) {
                val matching=context.contentResolver.getType(overwriteItem.uri)==options.format.mime
                OverwriteButton(overwriteItem,matching) {onOverwrite(options)}
            }
            TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) }
    }) {
            extraHint?.let { Text(it) }
            Text(strings.getString(R.string.file_format), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoFormat.entries.forEach { format -> FilterChip(options.format == format,
                    { options = options.copy(format = format) }, { Text(format.label) }) }
            }

            CompactChoice(strings.getString(R.string.export_max_edge,options.maxEdge),options.maxEdge,listOf(1280,1920,2560,4096,8192,16384),{strings.getString(R.string.edge_pixels,it)},{options=options.copy(maxEdge=it)})
            if (options.maxEdge >= 8192) Text(strings.getString(R.string.tiled_export_hint), style = MaterialTheme.typography.bodySmall)
            if (options.format == PhotoFormat.JPEG) {
                Text(strings.getString(R.string.export_quality, options.quality), style = MaterialTheme.typography.labelLarge)
                Slider(options.quality.toFloat(), { options = options.copy(quality = it.roundToInt()) }, valueRange = 60f..100f,
                    modifier = Modifier.semantics { contentDescription = strings.getString(R.string.jpeg_quality) })
                Text(strings.getString(R.string.jpeg_hint), style = MaterialTheme.typography.bodySmall)
            } else Text(strings.getString(R.string.png_hint), style = MaterialTheme.typography.bodySmall)
            if (options.format == PhotoFormat.JPEG) {
                CompactChoice(strings.getString(R.string.metadata_title),options.metadata,MetadataMode.entries,{strings.getString(it.labelRes)},{options=options.copy(metadata=it)})
                if (options.metadata == MetadataMode.CAMERA_AND_LOCATION)
                    Text(strings.getString(R.string.metadata_location_hint), style = MaterialTheme.typography.bodySmall)
            } else Text(strings.getString(R.string.metadata_png_hint), style = MaterialTheme.typography.bodySmall)
            Text(strings.getString(R.string.export_photo_location), style = MaterialTheme.typography.bodySmall)
            if(overwriteItem!=null && onOverwrite!=null && context.contentResolver.getType(overwriteItem.uri)!=options.format.mime)
                Text(strings.getString(R.string.overwrite_format_hint),style=MaterialTheme.typography.bodySmall)
    }
}