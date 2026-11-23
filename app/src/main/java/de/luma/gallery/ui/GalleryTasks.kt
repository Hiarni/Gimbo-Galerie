package de.luma.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.luma.gallery.GalleryState
import de.luma.gallery.GalleryViewModel
import de.luma.gallery.R
import de.luma.gallery.data.*

@Composable
internal fun GalleryTasks(state:GalleryState,transfer:TransferSnapshot,vm:GalleryViewModel,onFiles:()->Unit,onDuplicates:()->Unit,onMoods:()->Unit,onVault:()->Unit,onCloud:()->Unit={}) {
    val strings=appStrings()
    val context=androidx.compose.ui.platform.LocalContext.current
    val cloudInfos by remember(context) {CloudScheduler.observe(context)}.collectAsState(initial=emptyList())
    val cloud=cloudInfos.firstOrNull {it.state==androidx.work.WorkInfo.State.RUNNING}
    val vault by VaultController.state.collectAsState()
    val duplicate=state.duplicateTask
    val mood=state.moodAnalysis.task
    val duplicatePending=duplicate?.status in listOf(AnalysisStatus.RUNNING,AnalysisStatus.QUEUED,AnalysisStatus.PAUSED,AnalysisStatus.FAILED)
    val moodPending=mood?.status in listOf(AnalysisStatus.RUNNING,AnalysisStatus.QUEUED,AnalysisStatus.PAUSED,AnalysisStatus.FAILED)
    val transferPending=transfer.running || transfer.interrupted || transfer.moveSources.isNotEmpty()
    val exportPending=state.exporting || state.exportSnapshot.interrupted
    val backupPending=state.backupBusy || state.backupSnapshot.interrupted
    val count=listOf(duplicatePending,moodPending,transferPending,exportPending,backupPending,vault.running,cloud!=null).count {it}
    var expanded by remember {mutableStateOf(false)}
    if(count==0) return
    TextButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().padding(horizontal=12.dp)) {
        if(listOf(duplicate?.active==true,mood?.active==true,transfer.running,state.exporting,state.backupBusy,vault.running,cloud!=null).any {it}) {
            CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))
        }
        Text(strings.getString(R.string.background_tasks)+" · $count")
    }
    if(expanded) CompactDialog(strings.getString(R.string.background_tasks),{expanded=false},actions={TextButton(onClick={expanded=false}) {Text(strings.getString(R.string.close))}}) {
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(duplicatePending) TaskCard(strings.getString(R.string.duplicates_title),duplicate?.done?:0,duplicate?.total?:0,duplicate?.active==true,
                onOpen={expanded=false;onDuplicates()},onPause=if(duplicate?.active==true) ({vm.cancelDuplicateScan();Unit}) else null)
            if(moodPending) TaskCard(strings.getString(R.string.moods_title),mood?.done?:0,mood?.total?:0,mood?.active==true,
                onOpen={expanded=false;onMoods()},onPause=if(mood?.active==true) ({vm.pauseMoods();Unit}) else null)
            if(transferPending) TaskCard(strings.getString(R.string.transfer_channel),transfer.progress?.completed?:0,transfer.progress?.total?:0,transfer.running,
                onOpen={expanded=false;onFiles()})
            if(backupPending) TaskCard(strings.getString(R.string.backup_title),state.backupSnapshot.done,state.backupSnapshot.total,state.backupBusy,
                onPause=if(state.backupBusy) ({vm.cancelBackup();Unit}) else null,onResume=if(state.backupSnapshot.interrupted) ({vm.resumeBackup();Unit}) else null,onDiscard=if(state.backupSnapshot.interrupted) ({vm.discardBackup();Unit}) else null)
            if(exportPending) TaskCard(strings.getString(R.string.export_notification_title),state.exportSnapshot.completed,state.exportSnapshot.total,state.exporting,
                onPause=if(state.exporting) ({vm.cancelExport();Unit}) else null,onResume=if(state.exportSnapshot.interrupted) ({vm.resumeExport();Unit}) else null,onDiscard=if(state.exportSnapshot.interrupted) ({vm.discardExport();Unit}) else null,pauseLabel=R.string.cancel)
            if(cloud!=null) TaskCard(strings.getString(R.string.cloud_title),cloud.progress.getInt("done",0),cloud.progress.getInt("total",0),true,onOpen={expanded=false;onCloud()})
            if(vault.running) TaskCard(strings.getString(when(vault.operation) {"DELETE"->R.string.vault_deleting;"EXPORT"->R.string.vault_restoring;else->R.string.vault_importing}),vault.done,vault.total,true,onOpen={expanded=false;onVault()})
        }
    }
}

@Composable
private fun TaskCard(title:String,done:Int,total:Int,running:Boolean,onOpen:(()->Unit)?=null,onPause:(()->Unit)?=null,onResume:(()->Unit)?=null,onDiscard:(()->Unit)?=null,pauseLabel:Int=R.string.background_pause) {
    val strings=appStrings()
    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            if(total>0) {Text(strings.getString(R.string.review_progress,done,total),style=MaterialTheme.typography.bodySmall);LinearProgressIndicator(progress={done.toFloat()/total},modifier=Modifier.fillMaxWidth())}
            else if(running) LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                onOpen?.let {TextButton(onClick=it) {Text(strings.getString(R.string.background_open))}}
                onPause?.let {TextButton(onClick=it) {Text(strings.getString(pauseLabel))}}
                onResume?.let {TextButton(onClick=it) {Text(strings.getString(R.string.background_resume))}}
                onDiscard?.let {TextButton(onClick=it) {Text(strings.getString(R.string.cancel))}}
            }
        }
    }
}
