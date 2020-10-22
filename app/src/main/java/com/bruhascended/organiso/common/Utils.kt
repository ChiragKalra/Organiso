package com.bruhascended.organiso.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.PREF_DARK_THEME
import com.bruhascended.core.constants.getMimeType
import com.bruhascended.organiso.BuildConfig
import com.bruhascended.organiso.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


fun AppCompatActivity.setPrefTheme() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (prefs.getBoolean(PREF_DARK_THEME, false)) setTheme(R.style.DarkTheme)
    else setTheme(R.style.LightTheme)
}

fun AppCompatActivity.setupToolbar(
    toolbar: Toolbar, mTitle: String? = null, home : Boolean = true
) {
    setSupportActionBar(toolbar)
    supportActionBar?.apply {
        setDisplayHomeAsUpEnabled(home)
        setDisplayShowHomeEnabled(home)
        title = mTitle ?: return
    }
}

fun File.getSharable(mContext: Context) : Intent {
    val mmsTypeString = getMimeType(absolutePath)
    val contentUri = FileProvider.getUriForFile(
        mContext,
        "${BuildConfig.APPLICATION_ID}.fileProvider", this
    )
    mContext.grantUriPermission(
        BuildConfig.APPLICATION_ID, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
    )
    return Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, contentUri)
        type = mmsTypeString
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
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