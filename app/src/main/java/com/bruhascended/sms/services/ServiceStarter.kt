package com.bruhascended.sms.services

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder


class ServiceStarter: Service() {
    private lateinit var mSMSReceiver: SMSReceiver
    private lateinit var mIntentFilter: IntentFilter
    override fun onCreate() {
        super.onCreate()
        mSMSReceiver = SMSReceiver()
        mIntentFilter = IntentFilter()
        mIntentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        registerReceiver(mSMSReceiver, mIntentFilter)
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister the SMS receiver
        unregisterReceiver(mSMSReceiver)
    }
}