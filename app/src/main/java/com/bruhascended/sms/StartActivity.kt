package com.bruhascended.sms

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Telephony
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        sharedPref = getSharedPreferences("local", Context.MODE_PRIVATE)

        onPause()

        if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
            if (sharedPref.getBoolean(arg, false)) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                return
            } else {
                organise()
                return
            }
        }

        val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
        setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        startActivityForResult(setSmsAppIntent, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Unable to set as default SMS app.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (sharedPref.getBoolean(arg, false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        organise()
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun organise() {
        val dark = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dark_theme", false)
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        window.statusBarColor = getColor(if (dark) R.color.background_dark else R.color.background_light)
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
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
    }

    override fun onPause() {
        super.onPause()
        manager?.destroy()
    }
}