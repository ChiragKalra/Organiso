package com.bruhascended.organiso.common

import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.PREF_DARK_THEME
import com.bruhascended.core.constants.getMimeType
import com.bruhascended.organiso.BuildConfig
import com.bruhascended.organiso.R
import java.io.File
import kotlin.math.roundToInt


fun AppCompatActivity.setPrefTheme() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (prefs.getBoolean(PREF_DARK_THEME, false)) {
        setTheme(R.style.DarkTheme)
    } else {
        setTheme(R.style.LightTheme)
    }
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

fun AppCompatActivity.requestDefaultApp(onDefaultAppResult: ActivityResultLauncher<Intent>) {
    val intent = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        val roleManager = getSystemService(RoleManager::class.java)
        roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        setSmsAppIntent
    }
    onDefaultAppResult.launch(intent)
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