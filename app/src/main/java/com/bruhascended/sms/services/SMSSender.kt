package com.bruhascended.sms.services

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.provider.Telephony
import android.telephony.SmsManager
import android.widget.ImageButton
import android.widget.Toast
import com.bruhascended.sms.conversationDao
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.mainViewModel

/*
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later
*/

class SMSSender (
    private val mContext: Context,
    private var conversation: Conversation,
    private val sendButton: ImageButton
) {

    private fun addSmsToGlobal(message: Message) {
        val romsThatDontSaveSms = arrayOf("HUAWEI")
        if (android.os.Build.MANUFACTURER in romsThatDontSaveSms) {
            try {
                val values = ContentValues()
                values.put("address", message.sender)
                values.put("body", message.text)
                values.put("read", 0)
                values.put("date", message.time)
                values.put("type", message.type)
                mContext.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun addSmsToDb(smsText: String, date: Long, type: Int, delivered: Boolean) {
        Thread {
            val message = Message(
                null,
                conversation.sender,
                smsText,
                type,
                date,
                0,
                delivered
            )
            val qs = conversationDao.search(date)
            for (m in qs) conversationDao.delete(m)
            conversationDao.insert(message)
            if (type == 2) addSmsToGlobal(message)

            if (conversation.id == null) {
                var found = false
                for (i in 0..4) {
                    val res = mainViewModel.daos[i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        found = true
                        conversation = res[0]
                        break
                    }
                }
                conversation.time = date
                conversation.lastSMS = smsText
                if (found) mainViewModel.daos[conversation.label].update(conversation)
                else mainViewModel.daos[conversation.label].insert(conversation)
            } else {
                conversation.time = date
                conversation.lastSMS = smsText
                mainViewModel.daos[conversation.label].update(conversation)
            }
        }.start()
    }

    fun sendSMS(smsText: String) {
        sendButton.isEnabled = false
        val smsManager = SmsManager.getDefault()
        val date = System.currentTimeMillis()

        val sentPI = PendingIntent.getBroadcast(mContext, 0, Intent("SENT"), 0)
        val deliveredPI = PendingIntent.getBroadcast(mContext, 0, Intent("DELIVERED"), 0)

        addSmsToDb(smsText, date, 6, false)
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> addSmsToDb(smsText, date, 2, false)
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Toast.makeText(
                            mContext,
                            "Service provider error",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(smsText, date, 5, false)
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Toast.makeText(
                            mContext,
                            "No service",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(smsText, date, 5, false)
                    }
                }
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter("SENT"))
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> addSmsToDb(smsText, date, 2, true)
                    Activity.RESULT_CANCELED -> addSmsToDb(smsText, date, 2, false)
                }
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter("DELIVERED"))

        smsManager.sendTextMessage(conversation.sender, null, smsText, sentPI, deliveredPI)
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()

        sendButton.isEnabled = true
    }

}