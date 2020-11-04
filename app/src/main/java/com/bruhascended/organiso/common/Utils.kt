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
import com.bruhascended.core.constants.PREF_SEND_SPAM
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

fun AppCompatActivity.requestSpamReportPref() {
    val key = "UPLOAD_SPAM_CHOSEN"
    val mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    if (mPrefs.getBoolean(key, false)) return

    mPrefs.edit().putBoolean(key, true).apply()
    val root = LinearLayout(this)
    val text = TextView(this)
    val dp = resources.displayMetrics.density.roundToInt()
    root.addView(text)
    root.setPadding(dp*10, dp*10, 10*dp, 10*dp)
    text.text = getString(R.string.send_reports_query)
    text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    AlertDialog.Builder(this)
        .setView(root)
        .setPositiveButton(getString(R.string.send)) { dialog, _ ->
            mPrefs.edit().putBoolean(PREF_SEND_SPAM, true).apply()
            dialog.dismiss()
        }.setNegativeButton(getString(R.string.dont_send)) { dialog, _ ->
            mPrefs.edit().putBoolean(PREF_SEND_SPAM, false).apply()
            dialog.dismiss()
        }.create().show()
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