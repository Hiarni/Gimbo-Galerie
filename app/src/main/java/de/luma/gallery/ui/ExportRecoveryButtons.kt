package de.luma.gallery.ui

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.luma.gallery.R
import de.luma.gallery.data.ExportController
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun ExportRecoveryButtons(onResume:()->Unit,onDiscard:()->Unit) {
    val context=LocalContext.current;val strings=appStrings();val scope=rememberCoroutineScope()
    var discard by remember {mutableStateOf(false)};var busy by remember {mutableStateOf(false)};var error by remember {mutableStateOf(false)}
    var pendingUri by remember {mutableStateOf<Uri?>(null)}
    val resume by rememberUpdatedState(onResume);val remove by rememberUpdatedState(onDiscard)
    fun action() {if(discard) remove() else resume()}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {result->busy=false;if(result.resultCode==Activity.RESULT_OK) action()}
    fun grant(uri:Uri) {
        try {
            if(Build.VERSION.SDK_INT>=30) permission.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(context.contentResolver,listOf(uri)).intentSender).build())
            else try {context.contentResolver.openFileDescriptor(uri,"rw")?.use {} ?: throw java.io.IOException();busy=false;action()}
            catch(e:RecoverableSecurityException) {permission.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())}
        } catch(_:Exception) {busy=false;error=true}
    }
    val metadata=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        val uri=pendingUri;pendingUri=null
        if(granted && uri!=null) grant(uri) else {busy=false;error=true}
    }
    fun start(removing:Boolean) {
        if(busy) return
        busy=true;discard=removing
        scope.launch {
            try {
                val task=ExportController.read(context)
                if(task?.overwrite==true && task.completed==0 && (!removing || de.luma.gallery.data.SafeReplacement(context).needsRecovery(task.id))) {
                    val uri=task.items.single().uri
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED) {pendingUri=uri;metadata.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)}
                    else grant(uri)
                } else {busy=false;action()}
            } catch(e:Exception) {busy=false;if(e is CancellationException) throw e;error=true}
        }
    }
    Row {
        TextButton(onClick={start(false)},enabled=!busy) {Text(strings.getString(R.string.resume_export))}
        TextButton(onClick={start(true)},enabled=!busy) {Text(strings.getString(R.string.discard_export))}
    }
    if(error) AlertDialog(onDismissRequest={error=false},title={Text(strings.getString(R.string.check_access))},text={Text(strings.getString(R.string.export_recovery_access))},
        confirmButton={TextButton(onClick={error=false}) {Text(strings.getString(R.string.close))}})
}
