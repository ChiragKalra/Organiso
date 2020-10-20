package com.bruhascended.core.constants

import android.webkit.MimeTypeMap
import com.google.gson.Gson


fun String?.toLabelArray(): Array<Int> = Gson().fromJson(this,  Array<Int>::class.java)

fun Array<Int>.toJson(): String = Gson().toJson(this)

fun getMimeType(url: String): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(url)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
}