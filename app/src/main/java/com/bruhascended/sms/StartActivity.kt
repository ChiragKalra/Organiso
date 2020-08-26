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
import android.provider.Telephony
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.ui.MessageNotificationManager
import com.bruhascended.sms.ui.start.StartViewModel
import kotlinx.android.synthetic.main.activity_start.*


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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        sharedPref = getSharedPreferences("local", Context.MODE_PRIVATE)

        if (PackageManager.PERMISSION_DENIED in
            Array(perms.size){ActivityCompat.checkSelfPermission(this, perms[it])})
            ActivityCompat.requestPermissions(this, perms, 1)
        else messages()
    }

    @SuppressLint("SetTextI18n")
    private fun messages() {
        if (sharedPref.getBoolean(arg, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        startActivityForResult(setSmsAppIntent, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = messages()

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_start)

        pageViewModel = ViewModelProvider(this).get(StartViewModel::class.java).apply {
            progress.value = -1
            disc.value = 0
        }

        pageViewModel.progress.observe(this, {
            if (it > -1) {
                if (progressBar.indeterminateMode) {
                    progressBar.indeterminateMode = false
                    pageViewModel.disc.postValue(1)
                }
                progressText.text = "$it%"
                if (progressBar.progress != 0f) progressBar.setProgressWithAnimation(
                    it.toFloat(),
                    ((it - progressBar.progress) * 100).toLong()
                )
                else progressBar.progress = it.toFloat()
            }
        })

        pageViewModel.disc.observe(this, {
            infoText.text = pageViewModel.discStrings[it]
        })

        var preTimer: CountDownTimer? = null
        pageViewModel.eta.observe(this, {
            preTimer?.cancel()
            preTimer = object : CountDownTimer(it, 1000) {
                @SuppressLint("SetTextI18n")
                override fun onTick(millisUntilFinished: Long) {
                    val sec = (millisUntilFinished / 1000) % 60
                    val min = (millisUntilFinished / 1000) / 60
                    if ((0 < pageViewModel.progress.value!!) && (pageViewModel.progress.value!! < 100)) {
                        if (min > 0) etaText.text = "ETA ${min}min ${sec}sec"
                        else etaText.text = "ETA ${sec}sec"
                    } else etaText.text = ""
                }

                override fun onFinish() {}
            }.start()
        })

        val mnm = MessageNotificationManager(mContext)
        mnm.createNotificationChannel()

        Thread {
            manager = SMSManager(mContext)
            manager?.getMessages()

            manager?.getLabels(pageViewModel)

            pageViewModel.disc.postValue(2)
            manager?.saveMessages()

            startActivity(Intent(mContext, MainActivity::class.java))

            sharedPref.edit().putBoolean(arg, true).apply()
            (mContext as Activity).finish()
        }.start()
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        super.onPause()
        manager?.destroy()
    }
}