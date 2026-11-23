package de.luma.gallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.luma.gallery.data.AnalysisController
import de.luma.gallery.data.AnalysisKind
import kotlinx.coroutines.*

class AnalysisActionReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val kind=runCatching {AnalysisKind.valueOf(intent.getStringExtra("kind")?:"")}.getOrNull() ?: return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try {runCatching {AnalysisController.pause(context.applicationContext,kind)}} finally {pending.finish()}
        }
    }
}
