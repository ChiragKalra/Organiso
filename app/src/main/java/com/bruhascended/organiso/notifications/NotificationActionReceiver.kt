package com.bruhascended.organiso.notifications

import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.organiso.*
import com.bruhascended.core.db.*
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.core.db.MainDaoProvider

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

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        fun Context.cancelNotification(sender: String, id: Long?) {
            NotificationManagerCompat.from(this).cancel(id!!.toInt())
            sendBroadcast(
                Intent(this, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_SENDER, sender)
            )
        }
    }

    private lateinit var mContext: Context
    private lateinit var mIntent: Intent

    override fun onReceive(context: Context, intent: Intent) {
        mContext = context.applicationContext
        mIntent = intent

        when (intent.action) {
            ACTION_CANCEL -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
                NotificationDbFactory(mContext).get().apply {
                    manager().findBySender(sender).forEach {
                        manager().delete(it)
                    }
                    close()
                }
            }
            ACTION_COPY -> {
                val otp = intent.getStringExtra(EXTRA_OTP) ?: return
                val clipboard =
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post{
                    Toast.makeText(mContext, mContext.getString(R.string.otp_copied), Toast.LENGTH_LONG).show()
                }
            }
            ACTION_DELETE_OTP, ACTION_DELETE_MESSAGE -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                val message = intent.getSerializableExtra(EXTRA_MESSAGE) as Message
                MessageDbFactory(mContext).of(conversation.clean).apply {
                    manager().delete(message)
                    val last = manager().loadLastSync()
                    if (last == null) {
                        MainDaoProvider(mContext).getMainDaos()[conversation.label]
                            .delete(conversation)
                    } else {
                        conversation.apply {
                            read = true
                            lastMMS = last.path != null
                            lastSMS = last.text
                            time = last.time
                            MainDaoProvider(mContext).getMainDaos()[label].update(this)
                        }
                    }
                    close()
                }
                val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (id == -1) {
                    mContext.cancelNotification(conversation.clean, conversation.id)
                } else {
                    NotificationManagerCompat.from(mContext).cancel(id)
                }
            }
            ACTION_MARK_READ -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                conversation.read = true
                MainDaoProvider(mContext).getMainDaos()[conversation.label].update(conversation)
                mContext.cancelNotification(conversation.clean, conversation.id)
            }
            ACTION_REPLY -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                val replyText = RemoteInput.getResultsFromIntent(intent).getCharSequence(EXTRA_TEXT_REPLY).toString()

                val newMessage = Message(replyText, MESSAGE_TYPE_SENT, System.currentTimeMillis())
                MessageNotificationManager(mContext).sendSmsNotification(newMessage to conversation)

                SMSSender(mContext.applicationContext, arrayOf(conversation)).sendSMS(replyText)
            }
            ACTION_REPORT_SPAM -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                AnalyticsLogger(mContext).apply {
                    log("${conversation.label}_to_4")
                    reportSpam(conversation)
                }
                mContext.cancelNotification(conversation.clean, conversation.id)
                conversation.moveTo(LABEL_SPAM, mContext)
            }
        }
    }
}