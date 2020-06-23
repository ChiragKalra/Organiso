package com.bruhascended.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.ui.start.StartViewModel
import com.mikhaellopez.circularprogressbar.CircularProgressBar


class StartActivity : AppCompatActivity() {
    private lateinit var mContext: Context
    private lateinit var pageViewModel: StartViewModel
    private lateinit var sharedPref: SharedPreferences

    private val arg = "InitDataOrganized"

    private fun messages() {

        Thread(Runnable {
            val manager = SMSManager(mContext)
            manager.getMessages()

            pageViewModel.disc.postValue(1)
            manager.getLabels(pageViewModel)
            pageViewModel.disc.postValue(2)
            manager.saveMessages()

            startActivity(Intent(mContext, MainActivity::class.java))

            sharedPref.edit().putBoolean(arg, true).apply()
            (mContext as Activity).finish()
        }).start()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        mContext = this

        sharedPref = getSharedPreferences("local", Context.MODE_PRIVATE)

        if (sharedPref.getBoolean(arg, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        pageViewModel = ViewModelProvider(this).get(StartViewModel::class.java).apply {
            progress.value = 0
            disc.value = 0
        }

        val progressBar: CircularProgressBar = findViewById(R.id.progressBar)
        val progressTextView: TextView = findViewById(R.id.progressText)
        val discTextView: TextView = findViewById(R.id.infoText)

        pageViewModel.progress.observe(this, Observer<Int> {
            progressTextView.text = "$it%"
            progressBar.setProgressWithAnimation(it.toFloat(),
                ((it-progressBar.progress) * 100).toLong())
        })

        pageViewModel.disc.observe(this, Observer<Int> {
            discTextView.text = pageViewModel.discStrings[it]
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.SEND_SMS
                ),
                2)
        } else {
            messages()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
       messages()
    }

}