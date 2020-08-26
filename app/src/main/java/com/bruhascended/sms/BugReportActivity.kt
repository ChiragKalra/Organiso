package com.bruhascended.sms

import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.AnimatedVectorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_bug_report.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BugReportActivity : AppCompatActivity() {

    private lateinit var fileUri: Uri

    private fun loadMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "image/*"
        startActivityForResult(intent, 0)
    }

    private fun fadeAway(vararg views: View) {
        for (view in views) view.apply {
            if (visibility == View.VISIBLE) {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
                GlobalScope.launch {
                    delay(400)
                    runOnUiThread {
                        alpha = 1f
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun hideMediaPreview() {
        fadeAway(fileName)
        addFile.setImageResource(R.drawable.close_to_add)
        (addFile.drawable as AnimatedVectorDrawable).start()
        addFile.setOnClickListener {
            loadMedia()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme.equals("content")) {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
            cursor?.close()
        }
        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_bug_report)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        addFile.setOnClickListener {
            loadMedia()
        }

        submit.setOnClickListener {
            if (titleEditText.text.toString() != "" && full.text.toString() != "") {
                postBugReport()
                finish()
            }
        }
    }

    private fun postBugReport() {

        val firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.METHOD, "default")
        firebaseAnalytics.logEvent("bug_reported", bundle)

        Toast.makeText(this, "Bug Report sent", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && data != null && data.data != null) {
            fileUri = data.data!!
            fileName.text = getFileName(fileUri)
            fileName.visibility = View.VISIBLE
            addFile.apply {
                setImageResource(R.drawable.close)
                setOnClickListener {
                    hideMediaPreview()
                }
            }
        }
    }
}