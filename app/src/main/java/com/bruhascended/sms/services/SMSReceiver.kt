package com.bruhascended.sms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import com.bruhascended.sms.data.SMSManager


 class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pduObjects = bundle["pdus"] as Array<*>
                Thread( Runnable {
                    val smm = SMSManager(context)

                    for (aObject in pduObjects) {
                        val currentSMS = SmsMessage.createFromPdu(aObject as ByteArray, bundle.getString("format"))
                        val sender = currentSMS.displayOriginatingAddress.toString()
                        val content = currentSMS.messageBody.toString()
                        smm.putMessage(sender, content)
                    }

                    smm.getLabels()
                    smm.saveMessages()
                }).start()
            }
        }
    }
}
