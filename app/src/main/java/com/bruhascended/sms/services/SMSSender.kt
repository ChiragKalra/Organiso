package com.bruhascended.sms.services

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.widget.ImageButton
import android.widget.Toast
import com.bruhascended.sms.conversationDao
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.mainViewModel

class SMSSender (
    private val mContext: Context,
    private var conversation: Conversation,
    private val sendButton: ImageButton
) {

    //TODO private fun addSmsToGlobal(message: Message) {if huawei}

    private fun addSmsToDb(smsText: String, date: Long) {
        conversationDao.insert(
            Message(
                null,
                conversation.sender,
                smsText,
                2,
                date,
                0
            )
        )
        if (conversation.id == null) {
            var found = false
            for (i in 0..4) {
                val res = mainViewModel!!.daos!![i].findBySender(conversation.sender)
                if (res.isNotEmpty()) {
                    found = true
                    conversation = res[0]
                    break
                }
            }
            conversation.time = date
            conversation.lastSMS = smsText
            if (found)
                mainViewModel!!.daos!![conversation.label].update(conversation)
            else
                mainViewModel!!.daos!![conversation.label].insert(conversation)
        } else {
            conversation.time = date
            conversation.lastSMS = smsText
            mainViewModel!!.daos!![conversation.label].update(conversation)
        }

    }

    fun sendSMS(smsText: String) {
        sendButton.isEnabled = false
        Toast.makeText(
            mContext,
            "Sending",
            Toast.LENGTH_SHORT
        ).show()
        val smsManager = SmsManager.getDefault()
        val date = System.currentTimeMillis()

        val sentPI = PendingIntent.getBroadcast(mContext, 0, Intent("SENT"), 0)
        val deliveredPI = PendingIntent.getBroadcast(mContext, 0, Intent("DELIVERED"), 0)

        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(
                            mContext,
                            "SMS sent",
                            Toast.LENGTH_SHORT
                        ).show()
                        Thread{addSmsToDb(smsText, date)}.start()
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Toast.makeText(
                        mContext,
                        "Service provider error",
                        Toast.LENGTH_SHORT
                    ).show()
                    SmsManager.RESULT_ERROR_NO_SERVICE -> Toast.makeText(
                        mContext,
                        "No service",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                sendButton.isEnabled = true
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter("SENT"))
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> Toast.makeText(
                        mContext,
                        "SMS delivered",
                        Toast.LENGTH_SHORT
                    ).show()
                    Activity.RESULT_CANCELED -> Toast.makeText(
                        mContext,
                        "SMS not delivered",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter("DELIVERED"))

        smsManager.sendTextMessage(conversation.sender, null, smsText, sentPI, deliveredPI)
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()
    }

}