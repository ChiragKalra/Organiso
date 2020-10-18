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
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.organiso.BuildConfig.APPLICATION_ID
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationSender
import com.bruhascended.organiso.R
import com.bruhascended.core.data.SMSManager.Companion.ACTION_OVERWRITE_MESSAGE
import com.bruhascended.core.data.SMSManager.Companion.EXTRA_MESSAGE
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_FAILED
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_QUEUED
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_SENT
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.db.MainDaoProvider
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

class SMSSender(
    private val mContext: Context,
    private val conversations: Array<Conversation>,
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
        val message = Message (
            smsText, type, date, id = retryIndex, delivered = delivered
        )
        if (activeConversationSender != conversation.clean) {
            MessageDbFactory(mContext).of(conversation.clean).apply {
                val conversationDao = manager()
                val qs = conversationDao.search(date)
                for (m in qs) {
                    message.id = m.id
                    conversationDao.delete(m)
                }
                if (retryIndex != null) conversationDao.delete(message)
                conversationDao.insert(message)
                close()
            }
        } else {
            mContext.sendBroadcast (
                Intent(ACTION_OVERWRITE_MESSAGE).apply {
                    putExtra(EXTRA_MESSAGE, message)
                    setPackage(mContext.applicationInfo.packageName)
                }
            )
        }

        var newCon = conversation
        if (conversation.id == null) {
            for (i in 0..4) {
                val res = MainDaoProvider(mContext).getMainDaos()[i].findBySender(conversation.clean)
                if (res.isNotEmpty()) {
                    conversations[
                        conversations.indexOf(conversations.first {
                            it.clean == conversation.clean
                        })
                    ] = res.first()
                    newCon = res.first()
                    break
                }
            }
        }

        newCon.apply {
            time = date
            lastSMS = smsText
            read = true
            lastMMS = false
            if (newCon.id != null) MainDaoProvider(mContext).getMainDaos()[label].update(this)
            else MainDaoProvider(mContext).getMainDaos()[label].insert(this)
        }
    }

    fun sendSMS(smsText: String, retryIndex: Long? = null) {
        val date = System.currentTimeMillis()
        val transaction = Transaction(mContext, settings)

        conversations.forEach { conversation ->
            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_QUEUED, false, retryIndex)
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context, arg1: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_SENT,
                                false, retryIndex)
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.service_provider_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_FAILED,
                                false, retryIndex)
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.no_service),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_FAILED,
                                false, retryIndex)
                        }
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_FAILED,
                                false, retryIndex)
                        }
                    }
                    mContext.unregisterReceiver(this)
                }
            }, IntentFilter(sentAction))
            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context?, arg1: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK ->
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_SENT,
                                true, retryIndex)
                        else ->
                            addSmsToDb(conversation, smsText, date, MESSAGE_TYPE_SENT,
                                false, retryIndex)
                    }
                    mContext.unregisterReceiver(this)
                }
            }, IntentFilter(deliveredAction))

            val message = SMS(smsText, conversation.address)
            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
        }
    }

}
