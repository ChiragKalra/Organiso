package com.bruhascended.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.services.ServiceStarter
import com.bruhascended.sms.ui.start.StartViewModel
import com.mikhaellopez.circularprogressbar.CircularProgressBar


class StartActivity : AppCompatActivity() {
    private lateinit var mContext: Context
    private lateinit var pageViewModel: StartViewModel
    private lateinit var sharedPref: SharedPreferences
    private var manager: SMSManager? = null

    private val arg = "InitDataOrganized"

    private val perms = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    @SuppressLint("SetTextI18n")
    private fun messages() {
        if (sharedPref.getBoolean(arg, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_start)

        pageViewModel = ViewModelProvider(this).get(StartViewModel::class.java).apply {
            progress.value = -1
            disc.value = 0
        }

        val progressBar: CircularProgressBar = findViewById(R.id.progressBar)
        val progressTextView: TextView = findViewById(R.id.progressText)
        val discTextView: TextView = findViewById(R.id.infoText)
        val etaView: TextView = findViewById(R.id.etaText)

        pageViewModel.progress.observe(this, Observer<Int> {
            if (it>-1) {
                if (progressBar.indeterminateMode) {
                    progressBar.indeterminateMode = false
                    pageViewModel.disc.postValue(1)
                }
                progressTextView.text = "$it%"
                if (progressBar.progress != 0f) progressBar.setProgressWithAnimation(
                    it.toFloat(),
                    ((it - progressBar.progress) * 100).toLong()
                )
                else progressBar.progress = it.toFloat()
            }
        })

        pageViewModel.disc.observe(this, Observer<Int> {
            discTextView.text = pageViewModel.discStrings[it]
        })

        var preTimer: CountDownTimer? = null
        pageViewModel.eta.observe(this, Observer<Long> {
            preTimer?.cancel()
            preTimer = object : CountDownTimer(it, 1000) {
                @SuppressLint("SetTextI18n")
                override fun onTick(millisUntilFinished: Long) {
                    val sec = (millisUntilFinished / 1000) % 60
                    val min = (millisUntilFinished / 1000) / 60
                    if ((0 < pageViewModel.progress.value!!) && (pageViewModel.progress.value!! < 100)) {
                        if (min > 0) etaView.text = "ETA ${min}min ${sec}sec"
                        else etaView.text = "ETA ${sec}sec"
                    } else {
                        etaView.text = ""
                    }
                }
                override fun onFinish() {}
            }.start()
        })

        Thread(Runnable {
            manager = SMSManager(mContext)
            manager?.getMessages()

            manager?.getLabels(pageViewModel)

            pageViewModel.disc.postValue(2)
            manager?.saveMessages()

            startActivity(Intent(mContext, MainActivity::class.java))

            sharedPref.edit().putBoolean(arg, true).apply()
            (mContext as Activity).finish()
        }).start()
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        sharedPref = getSharedPreferences("local", Context.MODE_PRIVATE)
        startService(Intent(this, ServiceStarter::class.java))

        if (PackageManager.PERMISSION_DENIED in
            Array(perms.size){ActivityCompat.checkSelfPermission(this, perms[it])})
            ActivityCompat.requestPermissions(this, perms, 1)
        else messages()
    }

    override fun onPause() {
        super.onPause()
        manager?.destroy()
    }

    override fun onRequestPermissionsResult (
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = messages()
}