package com.bruhascended.sms.services

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log


class ServiceStarter: Service() {
    private lateinit var mSMSreceiver: SMSReceiver
    private lateinit var mIntentFilter: IntentFilter
    override fun onCreate() {
        super.onCreate()

        //SMS event receiver

        Log.d("s", "--------------------------")

        mSMSreceiver = SMSReceiver()
        mIntentFilter = IntentFilter()
        mIntentFilter.addAction("android.provider.Telephony.SMS_RECEIVED")
        registerReceiver(mSMSreceiver, mIntentFilter)
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregister the SMS receiver
        unregisterReceiver(mSMSreceiver)
    }
}