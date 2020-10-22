package com.bruhascended.core.constants

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.webkit.MimeTypeMap
import com.bruhascended.core.data.ContactsManager
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


fun String?.toLabelArray(): Array<Int> = Gson().fromJson(this, Array<Int>::class.java)

fun Array<Int>.toJson(): String = Gson().toJson(this)

fun getMimeType(url: String): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(url)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
}

fun deleteSMS(context: Context, message: String?, number: String) {
    val cm = ContactsManager(context)
    try {
        val uriSms: Uri = Uri.parse("content://sms/")
        val c: Cursor? = context.contentResolver.query(
            uriSms, arrayOf(
                "_id", "thread_id", "address",
                "person", "date", "body"
            ), null, null, null
        )
        if (c != null && c.moveToFirst()) {
            do {
                val id: Long = c.getLong(0)
                val address: String = cm.getClean(c.getString(2))
                val body: String = c.getString(5)
                if (message == null || message == body) {
                    if (address == number) {
                        context.contentResolver.delete(
                            Uri.parse("content://sms/$id"), null, null
                        )
                    }
                }
            } while (c.moveToNext())
        }
        c?.close()
    } catch (e: Exception) { }
}

fun Uri.saveFile(mContext: Context, fileName: String): String {
    val typeString = mContext.contentResolver.getType(this) ?: return path!!
    val name = fileName + "." +
            MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString)
    val destination = File(mContext.filesDir, name)
    val output: OutputStream = FileOutputStream(destination)
    val input = mContext.contentResolver.openInputStream(this)!!
    val buffer = ByteArray(4 * 1024)
    var read: Int
    while (input.read(buffer).also { read = it } != -1) {
        output.write(buffer, 0, read)
    }
    output.flush()
    return destination.absolutePath
}