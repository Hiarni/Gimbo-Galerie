package de.luma.gallery

import android.app.Application
import kotlinx.coroutines.launch


class LumaApplication: Application(), coil.ImageLoaderFactory, androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration get() = androidx.work.Configuration.Builder().build()
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .components { add(coil.decode.VideoFrameDecoder.Factory()) }.build()
    override fun onCreate() {
        super.onCreate()
        de.luma.gallery.data.VaultRepository(this).clearPreviews()
        if(getDatabasePath("gimbo-analysis.db").exists()) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {de.luma.gallery.data.AnalysisController.recover(this@LumaApplication)}
        }
        if(java.io.File(filesDir,"luma-project-sources").exists()) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO).launch {runCatching {de.luma.gallery.data.ProjectSourceProtection(this@LumaApplication).cleanupUnused()}}
        cacheDir.listFiles()?.filter { it.name.startsWith("luma_backup_") || it.name.startsWith("luma_tile_") ||
            it.name.startsWith("luma_export_") && it.extension == "mp4" }?.forEach { it.delete() }
    }
}
