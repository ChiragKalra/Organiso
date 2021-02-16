package com.bruhascended.organiso.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.EVENT_CONVERSATION_ORGANISED
import com.bruhascended.core.constants.KEY_RESUME_DATE
import com.bruhascended.core.constants.PARAM_BACKGROUND
import com.bruhascended.core.data.ContactsManager
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.organiso.ConversationActivity
import com.bruhascended.organiso.analytics.AnalyticsLogger
import com.bruhascended.organiso.notifications.MessageNotificationManager

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

class SMSReceiverService: Service() {

    private val mContext: Context = this

    private fun getDao(number: String): MessageDao {
        return if (ConversationActivity.activeConversationNumber == number) {
            ConversationActivity.activeConversationDao!!
        } else {
            MessageDbFactory(mContext).of(number).manager()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId)
        }

        AnalyticsLogger(mContext).log(EVENT_CONVERSATION_ORGANISED, PARAM_BACKGROUND)

        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            if (mContext.packageName != Telephony.Sms.getDefaultSmsPackage(mContext)) {
                SMSManager(mContext).updateSync()
            }
            return super.onStartCommand(intent, flags, startId)
        }

        val mnm = MessageNotificationManager(mContext)
        val cm = ContactsManager(mContext)
        val bundle = intent.extras
        if (bundle != null) Thread {
            val pduObjects = bundle["pdus"] as Array<*>
            val smm = SMSManager(mContext)
            val senders = hashMapOf<String, String>()

            for (aObject in pduObjects) {
                val currentSMS = SmsMessage.createFromPdu(aObject as ByteArray, bundle.getString("format"))
                val sender = currentSMS.displayOriginatingAddress
                val content = currentSMS.messageBody
                if (sender in senders) {
                    senders[sender] += content
                } else {
                    senders[sender] = content
                }
            }

            senders.forEach {
                it.apply {
                    val number = cm.getClean(key)
                    val res = smm.putMessage(
                        number,
                        value,
                        getDao(number),
                        ConversationActivity.activeConversationNumber == number
                    )
                    if (ConversationActivity.activeConversationNumber != number) {
                        mnm.sendSmsNotification(res)
                    }
                }
            }
            smm.close()
            PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit().putLong(KEY_RESUME_DATE, System.currentTimeMillis()).apply()
        }.start()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}