package com.bruhascended.organiso.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.preference.PreferenceManager
import com.bruhascended.core.data.ContactsManager
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.organiso.ConversationActivity
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationNumber
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

class SMSReceiver : BroadcastReceiver() {

    private lateinit var mContext: Context

    private fun getDao(number: String): MessageDao {
        return if (activeConversationNumber == number) {
            ConversationActivity.activeConversationDao!!
        } else {
            MessageDbFactory(mContext).of(number).manager()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (context.packageName != Telephony.Sms.getDefaultSmsPackage(context)) {
            if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
                SMSManager(context).updateSync()
            }
            return
        } else if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            return
        }


        val mnm = MessageNotificationManager(context)
        val cm = ContactsManager(context)
        mContext = context
        val bundle = intent.extras
        if (bundle != null) Thread {
            val pduObjects = bundle["pdus"] as Array<*>
            val smm = SMSManager(context)
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
                        activeConversationNumber == number
                    )
                    if (activeConversationNumber != number) {
                        mnm.sendSmsNotification(res)
                    }
                }
            }
            smm.close()
            PreferenceManager.getDefaultSharedPreferences(mContext)
                .edit().putLong(KEY_RESUME_DATE, System.currentTimeMillis()).apply()
        }.start()
    }
}
