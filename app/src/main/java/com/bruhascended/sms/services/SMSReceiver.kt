package com.bruhascended.sms.services

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.bruhascended.sms.data.IncomingSMSManager
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
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED" &&
            context.packageName == Telephony.Sms.getDefaultSmsPackage(context)) return

        val mnm = MessageNotificationManager(context)
        mContext = context
        val bundle = intent.extras
        if (bundle != null) Thread {
            val pduObjects = bundle["pdus"] as Array<*>
            val smm = IncomingSMSManager(context)
            var sender = ""
            var content = ""

            for (aObject in pduObjects) {
                val currentSMS = SmsMessage.createFromPdu(aObject as ByteArray, bundle.getString("format"))
                sender = currentSMS.displayOriginatingAddress.toString()
                content += currentSMS.messageBody.toString()
            }
            mnm.sendSmsNotification(smm.putMessage(sender, content))
            saveSms(sender, content)
        }.start()
    }
}
