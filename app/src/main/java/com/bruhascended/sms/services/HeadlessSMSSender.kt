package com.bruhascended.sms.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSMSSender: Service() {
    override fun onCreate() {
        //TODO
        super.onCreate()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}