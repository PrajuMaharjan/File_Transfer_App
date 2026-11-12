package com.prajumaharjan.filetransfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class MediaStoreSink (private val context: Context) : FileSink {
    private val uris = ConcurrentHashMap<String,Uri>()

    private fun legacyDir(): File{
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base,"FileTransfer").also { it.mkdirs() }
    }

    override fun open(name: String): OutputStream {
        if(Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,name)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FileTransfer")
                put(MediaStore.Downloads.IS_PENDING,1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
                    ?: error("Cannot create file")
            uris[name] = uri
            return context.contentResolver.openOutputStream(uri) ?: error("Cannot open file")
        }
        return FileOutputStream(File(legacyDir(),name))
    }

    override fun commit(name: String) {
        val uri = uris.remove(name) ?: return
        val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) }
        context.contentResolver.update(uri,values,null,null)
    }

    override fun discard(name: String) {
        val uri = uris.remove(name)
        if (uri != null) context.contentResolver.delete(uri,null,null)
        else File(legacyDir(),name).delete()
    }
}