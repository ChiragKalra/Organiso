/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.bruhascended.sms.services

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.telephony.SmsManager
import android.widget.ImageButton
import android.widget.Toast
import com.bruhascended.sms.activeConversationDao
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.BuildConfig.APPLICATION_ID
import com.klinker.android.send_message.BroadcastUtils
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Message as SMS

/*
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later
*/


class SMSSender(
    private val mContext: Context,
    private var conversation: Conversation,
    private val sendButton: ImageButton
) {

    private val sentAction = "$APPLICATION_ID.SMS_SENT"
    private val deliveredAction = "$APPLICATION_ID.SMS_DELIVERED"

    private val settings = Settings().apply {
        useSystemSending = true
        deliveryReports = true
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
            val qs = activeConversationDao.search(date)
            for (m in qs) activeConversationDao.delete(m)
            activeConversationDao.insert(message)

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
        val date = System.currentTimeMillis()

        val transaction = Transaction(mContext, settings).apply {
            setExplicitBroadcastForSentSms(Intent(sentAction))
            setExplicitBroadcastForDeliveredSms(Intent(deliveredAction))
        }

        addSmsToDb(smsText, date, 6, false)
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent?) {
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
        }, IntentFilter(sentAction))
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> addSmsToDb(smsText, date, 2, true)
                    else -> addSmsToDb(smsText, date, 2, false)
                }
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter(deliveredAction))

        val message = SMS(smsText, conversation.sender)
        transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)

        sendButton.isEnabled = true
    }

}