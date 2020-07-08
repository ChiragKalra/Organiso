package com.bruhascended.sms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, ServiceStarter::class.java)
        context.startService(service)
    }
}