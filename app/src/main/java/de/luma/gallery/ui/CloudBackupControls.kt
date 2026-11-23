package de.luma.gallery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.luma.gallery.R
import de.luma.gallery.data.*
import de.luma.gallery.localization.appLocale
import de.luma.gallery.localization.userMessage
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudBackupControls() {
    val context=LocalContext.current;val strings=appStrings();val scope=rememberCoroutineScope()
    val repository=remember {CloudBackup(context.applicationContext)}
    val state by repository.states.collectAsState(initial=repository.status())
    val jobs by remember {CloudScheduler.observe(context)}.collectAsState(initial=emptyList())
    val running=jobs.firstOrNull {it.state==androidx.work.WorkInfo.State.RUNNING}
    var review by remember {mutableStateOf<List<Pair<String,String>>?>(null)}
    var busy by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var consent by remember {mutableStateOf(false)}
    fun run(action:suspend()->Unit) {busy=true;error=null;scope.launch {
        try {withContext(Dispatchers.IO) {action()}} catch(e:Exception) {if(e is CancellationException) throw e;error=e.userMessage(strings,R.string.cloud_failed)} finally {busy=false}
    } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri->if(uri!=null) run {repository.choose(uri)}}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(strings.getString(R.string.cloud_title),style=MaterialTheme.typography.titleMedium)
        Text(strings.getString(R.string.cloud_provider_hint),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick={consent=true},enabled=!busy&&!state.automatic&&running==null,modifier=Modifier.fillMaxWidth()) {
            Icon(LumaIcons.Cloud,null);Spacer(Modifier.width(8.dp));Text(strings.getString(if(state.tree==null) R.string.cloud_choose_folder else R.string.cloud_change_folder))
        }
        if(state.tree!=null) {
            Text(state.destinationName ?: strings.getString(R.string.cloud_configured),style=MaterialTheme.typography.labelMedium)
            fun update(value:CloudStatus)=repository.save(value)
            Row {Text(strings.getString(R.string.cloud_automatic),Modifier.weight(1f));Switch(state.automatic,{update(state.copy(automatic=it))})}
            Row {Text(strings.getString(R.string.cloud_wifi),Modifier.weight(1f));Switch(state.wifi,{update(state.copy(wifi=it))})}
            Row {Text(strings.getString(R.string.cloud_charging),Modifier.weight(1f));Switch(state.charging,{update(state.copy(charging=it))})}
            Row {Text(strings.getString(R.string.photos),Modifier.weight(1f));Switch(state.photos,{update(state.copy(photos=it))})}
            Row {Text(strings.getString(R.string.videos),Modifier.weight(1f));Switch(state.videos,{update(state.copy(videos=it))})}
            EditorSlider(strings.getString(R.string.cloud_interval),state.hours.toFloat(),1f..24f,{update(state.copy(hours=it.toInt()))},{})
            Row {Text(strings.getString(R.string.cloud_mirror),Modifier.weight(1f));Switch(state.mirror,{update(state.copy(mirror=it))})}
            Text(strings.getString(R.string.cloud_delete_hint),style=MaterialTheme.typography.bodySmall)
            state.lastSync.takeIf {it>0}?.let {Text(strings.getString(R.string.cloud_last,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,strings.appLocale).format(Date(it)),state.count),style=MaterialTheme.typography.bodySmall)}
            Button(onClick={CloudScheduler.now(context)},enabled=!busy&&running==null&&(state.photos||state.videos),modifier=Modifier.fillMaxWidth()) {Text(strings.getString(R.string.cloud_sync_now))}
            if(running!=null) { LinearProgressIndicator(Modifier.fillMaxWidth());Text(strings.getString(R.string.cloud_progress,running.progress.getInt("done",0),running.progress.getInt("total",0),"")) }
            TextButton(onClick={CloudScheduler.cancel(context)}) {Text(strings.getString(R.string.cancel))}
            if(state.mirror) OutlinedButton(onClick={run {val list=repository.deletionReview();withContext(Dispatchers.Main) {review=list}}},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(strings.getString(R.string.cloud_review_deletions))}
            Text(strings.getString(R.string.cloud_restore_hint),style=MaterialTheme.typography.bodySmall)
            Text(strings.getString(R.string.cloud_schedule_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (error?:state.message)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text(strings.getString(R.string.cloud_title))},text={Text(strings.getString(R.string.cloud_consent))},
        confirmButton={TextButton(onClick={consent=false;picker.launch(state.tree?.let(android.net.Uri::parse))}) {Text(strings.getString(R.string.continue_action))}},dismissButton={TextButton(onClick={consent=false}) {Text(strings.getString(R.string.cancel))}})
    review?.let {list->AlertDialog(onDismissRequest={review=null},title={Text(strings.getString(R.string.cloud_review_deletions))},text={
        Text(if(list.isEmpty()) strings.getString(R.string.cloud_no_deletions) else strings.getString(R.string.cloud_delete_review,list.size)+"\n\n"+list.take(6).joinToString("\n") {it.second})
    },confirmButton={TextButton(onClick={review=null;run {repository.deleteConfirmed(list.map {it.first}.toSet())}},enabled=list.isNotEmpty()) {Text(strings.getString(R.string.permanently_delete))}},dismissButton={TextButton(onClick={review=null}) {Text(strings.getString(R.string.cancel))}})}
}
