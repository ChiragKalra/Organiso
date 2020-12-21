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
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.data.SMSManager
import com.bruhascended.organiso.common.requestDefaultApp
import com.bruhascended.organiso.notifications.ChannelManager
import com.bruhascended.organiso.ui.start.PolicyActivity
import kotlinx.android.synthetic.main.activity_start.*
import kotlin.math.min
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

    private val wakeLockTag = "Organiso::Wakelock"
    private val timeout = 10 * 60 * 1000L // stay awake for

    private lateinit var sharedPref: SharedPreferences

    private val discStrings = arrayOf(
        R.string.getting_your_msgs,
        R.string.organising_your_msgs,
        R.string.done
    )

    private val onDefaultAppResult = registerForActivityResult(StartActivityForResult()) {
        if (PackageManager.PERMISSION_DENIED in
            Array(ARR_PERMS.size) { ActivityCompat.checkSelfPermission(this, ARR_PERMS[it]) }
        ) {
            ActivityCompat.requestPermissions(this, ARR_PERMS, 0)
        } else {
            acceptTnc()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != Intent.ACTION_MAIN) {
            finish()
            return
        }

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        if (sharedPref.getBoolean(KEY_INIT, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // set default sim card slot if dual sim
        val sm = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (sm.activeSubscriptionInfoCount == 2) {
                sharedPref.edit()
                    .putBoolean(
                        PREF_ALTERNATE_SIM,
                        SmsManager.getDefaultSmsSubscriptionId()
                                == sm.activeSubscriptionInfoList[1].subscriptionId
                    ).apply()
            }
        }

        when {
            packageName != Telephony.Sms.getDefaultSmsPackage(this) -> {
                requestDefaultApp(onDefaultAppResult)
            }
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_DENIED ->
            {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    0
                )
            }
            else -> {
                acceptTnc()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            finish()
            return
        } else {
            acceptTnc()
        }
    }

    private fun acceptTnc() {
        // setup activity
        setTheme(R.style.LightTheme)
        setContentView(R.layout.activity_start)

        if (sharedPref.getBoolean(KEY_ACCEPT_TNC, false)) {
            // start organising
            policyScreen.apply {
                animate().alpha(0f)
                    .translationX(-measuredWidth.toFloat()).setDuration(700).start()
                policyScreen.postDelayed({
                    isVisible = false
                }, 700)
            }
            organise()
        } else {
            // ask the user to accept TnC and privacy policy
            val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            val mContext = this
            var strIn = getString(R.string.terms_and_conditions)
            var strOut = getString(R.string.agree_to_tnc, strIn)
            var start = strOut.indexOf(strIn)
            var strBuilder = SpannableStringBuilder(strOut)
            strBuilder.setSpan(object : ClickableSpan() {
                override fun onClick(view: View) {
                    startActivity(
                        Intent(mContext, PolicyActivity::class.java).apply {
                            putExtra(Intent.EXTRA_FROM_STORAGE,"terms_and_conditions.html")
                            putExtra(Intent.EXTRA_TITLE, strIn)
                        }
                    )
                }
            }, start, start + strIn.length, flag)
            tnc.text = strBuilder
            tnc.movementMethod = LinkMovementMethod.getInstance()

            strIn = getString(R.string.privacy_policy)
            strOut = getString(R.string.read_privacy_policy, strIn)
            start = strOut.indexOf(strIn)
            strBuilder = SpannableStringBuilder(strOut)
            strBuilder.setSpan(object : ClickableSpan() {
                override fun onClick(view: View) {
                    startActivity(
                        Intent(mContext, PolicyActivity::class.java).apply {
                            putExtra(Intent.EXTRA_FROM_STORAGE,"privacy_policy.html")
                            putExtra(Intent.EXTRA_TITLE, strIn)
                        }
                    )
                }
            }, start, start + strIn.length, flag)
            privacy.text = strBuilder
            privacy.movementMethod = LinkMovementMethod.getInstance()

            lateEntry.apply {
                alpha = 0f
                postDelayed({
                    animate().setDuration(700).alpha(1f).start()
                }, 700)
            }


            tnc.setOnCheckedChangeListener { _, b ->
                next.isEnabled = b && privacy.isChecked
            }
            privacy.setOnCheckedChangeListener { _, b ->
                next.isEnabled = b && tnc.isChecked
            }
            next.setOnClickListener {
                policyScreen.isVisible = false
                organise()

                sharedPref.edit().putBoolean(KEY_ACCEPT_TNC, true).apply()
            }
        }
    }

    private fun organise() {

        val smsManager = SMSManager(this)

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
        var minTime = Long.MAX_VALUE
        smsManager.onEtaChangeListener =  {
            runOnUiThread {
                preTimer?.cancel()
                preTimer = object : CountDownTimer(it, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        minTime = min(minTime, millisUntilFinished)
                        val sec = (minTime / 1000) % 60
                        val min = (minTime / 1000) / 60
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
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag).apply {
                    acquire(timeout)
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