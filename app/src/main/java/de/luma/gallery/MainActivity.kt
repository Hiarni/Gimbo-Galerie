package de.luma.gallery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import de.luma.gallery.ui.GalleryApp

class MainActivity : AppCompatActivity() {
    override fun onStop() {de.luma.gallery.data.VaultController.lock(this);super.onStop()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        de.luma.gallery.data.CloudScheduler.trash(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                de.luma.gallery.data.AnalysisController.recover(this@MainActivity)
                de.luma.gallery.data.TaskRecovery.recover(this@MainActivity)
            }
        }
        setContent { GalleryApp(viewModel()) }
    }
}
