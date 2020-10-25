package com.bruhascended.core.constants

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max

fun String?.toLabelArray(): Array<Int> = Gson().fromJson(this, Array<Int>::class.java)

fun Array<Int>.toJson(): String = Gson().toJson(this)

fun getMimeType(url: String): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(url)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
}

// SMS utils

fun Context.deleteSMS(id: Int) {
    if (packageName == Telephony.Sms.getDefaultSmsPackage(this)) {
        contentResolver.delete(
            Uri.parse("content://sms/$id"),
            null, null
        )
    }
}

fun Context.saveSms(phoneNumber: String, message: String, type: Int): Int? {
    if(packageName != Telephony.Sms.getDefaultSmsPackage(this)) return null
    val date = System.currentTimeMillis()
    val values = ContentValues().apply {
        put("address", phoneNumber)
        put("body", message)
        put("read", 1)
        put("date", date)
        put("type", type)
    }
    val uri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
    return uri?.lastPathSegment?.toInt()
}

fun Context.saveFile(data: Uri?, fileName: String): String? {
    if (data == null) return null
    val typeString = contentResolver.getType(data) ?: return data.path!!
    val name = fileName + "." +
            MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString)
    val destination = File(filesDir, name)
    val output: OutputStream = FileOutputStream(destination)
    val input = contentResolver.openInputStream(data)!!
    val buffer = ByteArray(4 * 1024)
    var read: Int
    while (input.read(buffer).also { read = it } != -1) {
        output.write(buffer, 0, read)
    }
    output.flush()
    return destination.absolutePath
}