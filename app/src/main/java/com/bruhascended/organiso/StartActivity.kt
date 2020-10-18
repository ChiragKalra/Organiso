package com.bruhascended.organiso

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.provider.Telephony
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.bruhascended.core.data.SMSManager
import com.bruhascended.organiso.notifications.ChannelManager
import kotlinx.android.synthetic.main.activity_start.*
import kotlin.math.roundToInt

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

    companion object {
        const val TAG_WAKE_LOCK = "Organiso::Wakelock"
        const val KEY_INIT = "InitDataOrganized"
    }

    private lateinit var mContext: Context
    private lateinit var sharedPref: SharedPreferences

    private val perms = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val discStrings = arrayOf(
        R.string.getting_your_msgs,
        R.string.organising_your_msgs,
        R.string.done
    )

    private val onPermissionsResult =
        registerForActivityResult(StartActivityForResult()) { organise() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)


        if (sharedPref.getBoolean(KEY_INIT, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (PackageManager.PERMISSION_DENIED in
            Array(perms.size){ ActivityCompat.checkSelfPermission(this, perms[it])})
            ActivityCompat.requestPermissions(this, perms, 0)
        else organise()
    }

    override fun onRequestPermissionsResult (
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
            onPermissionsResult.launch(setSmsAppIntent)
        }
        organise()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun organise() {
        setTheme(R.style.LightTheme)
        setContentView(R.layout.activity_start)

        val smsManager = SMSManager(mContext)

        var progress = 0f

        smsManager.onProgressListener = {
            runOnUiThread {
                progress = it
                if (it > 0) {
                    if (progressBar.indeterminateMode) {
                        progressBar.indeterminateMode = false
                        smsManager.onStatusChangeListener(1)
                    }
                    progressText.text = getString(R.string.x_percent, it.roundToInt())
                    progressBar.progress = it
                }
            }
        }

        smsManager.onStatusChangeListener = {
            runOnUiThread {
                infoText.text = getString(discStrings[it])
                if (it == 2) etaText.visibility = View.INVISIBLE
            }
        }
        smsManager.onStatusChangeListener(0)

        var preTimer: CountDownTimer? = null
        smsManager.onEtaChangeListener =  {
            runOnUiThread {
                preTimer?.cancel()
                preTimer = object : CountDownTimer(it, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val sec = (millisUntilFinished / 1000) % 60
                        val min = (millisUntilFinished / 1000) / 60
                        if ((0 < progress) && (progress < 100)) {
                            etaText.text = when {
                                min > 0 -> getString(R.string.x_mins_remaining, min)
                                sec > 9 -> getString(R.string.less_than_min_remaining)
                                sec > 3 -> getString(R.string.just_few_secs)
                                else -> getString(R.string.finishing_up)
                            }
                        } else etaText.text = ""
                    }
                    override fun onFinish() {}
                }.start()
            }
        }

        ChannelManager(this).createNotificationChannels()

        val wakeLock: PowerManager.WakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG_WAKE_LOCK).apply {
                    acquire(10*60*1000L)
                }
            }
        Thread {
            smsManager.apply {
                getMessages()
                getLabels()
            }
            startActivity(Intent(this, MainActivity::class.java))
            wakeLock.release()

            sharedPref.edit().putBoolean(KEY_INIT, true).apply()
            finish()
        }.start()
    }
}