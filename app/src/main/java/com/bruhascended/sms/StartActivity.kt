package com.bruhascended.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Telephony
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.ui.common.MessageNotificationManager
import com.bruhascended.sms.ui.start.StartViewModel
import kotlinx.android.synthetic.main.activity_start.*
import java.lang.Math.round

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

class StartActivity : AppCompatActivity() {
    private lateinit var mContext: Context
    private lateinit var pageViewModel: StartViewModel
    private lateinit var sharedPref: SharedPreferences

    private val arg = "InitDataOrganized"

    private val perms = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        sharedPref = getSharedPreferences("local", Context.MODE_PRIVATE)


        if (sharedPref.getBoolean(arg, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (PackageManager.PERMISSION_DENIED in
            Array(perms.size){ ActivityCompat.checkSelfPermission(this, perms[it])})
            ActivityCompat.requestPermissions(this, perms, 1)
        else messages()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) = messages()

    private fun messages() {
        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
            startActivityForResult(setSmsAppIntent, 1)
        } else {
            organise()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        organise()
        super.onActivityResult(requestCode, resultCode, data)
    }

    @SuppressLint("SetTextI18n")
    private fun organise() {
        if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES ) {
            setTheme(R.style.DarkTheme)
            window.statusBarColor = getColor(R.color.background_dark)
        } else {
            setTheme(R.style.LightTheme)
            window.statusBarColor = getColor(R.color.background_light)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        setContentView(R.layout.activity_start)

        pageViewModel = ViewModelProvider(this).get(StartViewModel::class.java).apply {
        progress.value = 0f
        disc.value = 0
    }
        pageViewModel.progress.observe(this, {
            if (it > 0) {
                if (progressBar.indeterminateMode) {
                    progressBar.indeterminateMode = false
                    pageViewModel.disc.postValue(1)
                }
                progressText.text = "${round(it)}%"
                progressBar.progress = it
            }
        })

        pageViewModel.disc.observe(this, {
            infoText.text = pageViewModel.discStrings[it]
            if (it == 2) etaText.visibility = View.INVISIBLE
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
                        etaText.text = when {
                            min > 0 -> "ETA ${min}min"
                            sec > 9 -> "Less than a minute remaining"
                            sec > 3 -> "Just a few more seconds"
                            else -> "Finishing Up"
                        }
                    } else etaText.text = ""
                }
                override fun onFinish() {}
            }.start()
        })

        MessageNotificationManager(mContext).createNotificationChannel()

        Thread {
            val wakeLock: PowerManager.WakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SMS Organiser::MyWakelock").apply {
                        acquire(10*60*1000L)
                    }
                }
            SMSManager(mContext, pageViewModel).apply {
                getMessages()
                getLabels()
            }

            startActivity(Intent(mContext, MainActivity::class.java))
            wakeLock.release()

            sharedPref.edit().putBoolean(arg, true).apply()
            (mContext as Activity).finish()
        }.start()
    }
}