package de.luma.gallery.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.net.Uri


class LibraryObserver(context: Context, changed: () -> Unit) : AutoCloseable {
    private val resolver = context.applicationContext.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val update = Runnable(changed)
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { handler.removeCallbacks(update); handler.postDelayed(update, 600) }
    }
    init { resolver.registerContentObserver(Uri.parse("content://media"), true, observer) }
    override fun close() { handler.removeCallbacks(update); resolver.unregisterContentObserver(observer) }
}
