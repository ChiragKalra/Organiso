package com.bruhascended.sms.services

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.ui.MessageNotificationManager


class SMSReceiver : BroadcastReceiver() {

     private lateinit var mContext: Context

    private fun saveSms(phoneNumber: String, message: String): Boolean {
        var ret = false
        try {
            val values = ContentValues()
            values.put("address", phoneNumber)
            values.put("body", message)
            values.put("read", 0)
            values.put("date", System.currentTimeMillis())
            values.put("type", 1)
            mContext.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            ret = true
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return ret
    }

    override fun onReceive(context: Context, intent: Intent) {
        val mnm = MessageNotificationManager(context)
        mnm.createNotificationChannel()
        mContext = context
        if (intent.action == "android.provider.Telephony.SMS_DELIVER") {
            val bundle = intent.extras
            if (bundle != null) Thread {
                val pduObjects = bundle["pdus"] as Array<*>
                val smm = SMSManager(context)

                for (aObject in pduObjects) {
                    val currentSMS = SmsMessage.createFromPdu(aObject as ByteArray, bundle.getString("format"))
                    val sender = currentSMS.displayOriginatingAddress.toString()
                    val content = currentSMS.messageBody.toString()
                    smm.putMessage(sender, content)
                    saveSms(sender, content)
                }

                smm.getLabels()
                for (pair in smm.saveMessages()) mnm.sendSmsNotification(pair.second, pair.first)
            }.start()
        }
    }
}
