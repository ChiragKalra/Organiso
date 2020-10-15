package com.bruhascended.organiso.services

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import androidx.room.Room
import com.bruhascended.organiso.activeConversationDao
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.mainViewModel
import com.bruhascended.organiso.BuildConfig.APPLICATION_ID
import com.bruhascended.organiso.R
import com.bruhascended.organiso.activeConversationSender
import com.bruhascended.organiso.db.MessageDatabase
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import com.klinker.android.send_message.Message as SMS

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
    private val conversations: Array<Conversation>
) {

    private val sentAction = "$APPLICATION_ID.SMS_SENT"
    private val deliveredAction = "$APPLICATION_ID.SMS_DELIVERED"

    private val settings = Settings().apply {
        useSystemSending = true
        deliveryReports = true
    }

    private fun addSmsToDb(
        conversation: Conversation, smsText: String,
        date: Long, type: Int, delivered: Boolean, retryIndex: Long?
    ) {
        Thread {
            val message = Message(
                retryIndex,
                smsText,
                type,
                date,
                0,
                delivered
            )
            val conversationDao = if (activeConversationSender == null) Room.databaseBuilder(
                mContext, MessageDatabase::class.java, conversation.sender
            ).allowMainThreadQueries().build().manager() else activeConversationDao
            val qs = conversationDao.search(date)
            for (m in qs) {
                message.id = m.id
                conversationDao.delete(m)
            }
            if (retryIndex != null) conversationDao.delete(message)
            conversationDao.insert(message)

            var newCon = conversation
            if (conversation.id == null) {
                for (i in 0..4) {
                    val res = mainViewModel.daos[i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        conversations[
                            conversations.indexOf(conversations.first {
                                it.sender == conversation.sender
                            })
                        ] = res[0]
                        newCon = res[0]
                        break
                    }
                }
            }

            newCon.apply {
                time = date
                lastSMS = smsText
                read = true
                lastMMS = false
                if (newCon.id != null) mainViewModel.daos[label].update(this)
                else mainViewModel.daos[label].insert(this)
            }
        }.start()
    }

    fun sendSMS(smsText: String, retryIndex: Long? = null) {
        val date = System.currentTimeMillis()
        val transaction = Transaction(mContext, settings)/*.apply {
            setExplicitBroadcastForSentSms(Intent(sentAction))
            setExplicitBroadcastForDeliveredSms(Intent(deliveredAction))
        }*/

        conversations.forEach { conversation ->
            addSmsToDb(conversation, smsText, date, 6, false, retryIndex)
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context, arg1: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            addSmsToDb(conversation, smsText, date, 2, false, retryIndex)
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.service_provider_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, 5, false, retryIndex)
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.no_service),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, 5, false, retryIndex)
                        }
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, 5, false, retryIndex)
                        }
                    }
                    mContext.unregisterReceiver(this)
                }
            }, IntentFilter(sentAction))
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context?, arg1: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            addSmsToDb(conversation, smsText, date, 2, true, retryIndex)
                        else ->
                            addSmsToDb(conversation, smsText, date, 2, false, retryIndex)
                    }
                    mContext.unregisterReceiver(this)
                }
            }, IntentFilter(deliveredAction))

            val message = SMS(smsText, conversation.sender)
            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
        }
    }

}
