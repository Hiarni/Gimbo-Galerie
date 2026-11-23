package de.luma.gallery.ui

import android.Manifest
import android.app.RecoverableSecurityException
import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.luma.gallery.R
import de.luma.gallery.model.*

@Composable
fun OverwriteButton(item:GalleryMedia,enabled:Boolean=true,onOverwrite:()->Unit) {
    val strings=appStrings();val context=LocalContext.current
    var confirming by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf(false)}
    val action by rememberUpdatedState(onOverwrite)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {result->
        if(result.resultCode==Activity.RESULT_OK) action()
    }
    fun grant() {
        confirming=false
        try {
            if(Build.VERSION.SDK_INT>=30) permission.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(context.contentResolver,listOf(item.uri)).intentSender).build())
            else {
                context.contentResolver.openFileDescriptor(item.uri,"rw")?.use {} ?: throw java.io.IOException()
                action()
            }
        } catch(e:RecoverableSecurityException) {permission.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())}
        catch(_:Exception) {error=true}
    }
    val location=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted) grant() else error=true}
    TextButton(onClick={confirming=true},enabled=enabled&&canManageMedia(item)) {Text(strings.getString(R.string.overwrite_original))}
    if(confirming) CompactDialog(strings.getString(R.string.overwrite_original),{confirming=false},actions={
        TextButton(onClick={confirming=false}) {Text(strings.getString(R.string.cancel))}
        TextButton(onClick={
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED)
                location.launch(Manifest.permission.ACCESS_MEDIA_LOCATION) else grant()
        }) {Text(strings.getString(R.string.overwrite_original))}
    }) {
        Text(strings.getString(R.string.overwrite_review,item.name))
        Text(strings.getString(R.string.overwrite_metadata_access),style=MaterialTheme.typography.bodySmall)
    }
    if(error) CompactDialog(strings.getString(R.string.check_access),{error=false},actions={TextButton(onClick={error=false}) {Text(strings.getString(R.string.close))}}) {
        Text(strings.getString(R.string.overwrite_permission_hint))
    }
}
