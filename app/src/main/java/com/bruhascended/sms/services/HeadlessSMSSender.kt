package com.bruhascended.sms.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.bruhascended.sms.db.Conversation

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

class HeadlessSMSSender : Service() {

    private fun getRecipients(uri: Uri): String {
        val base: String = uri.schemeSpecificPart
        val pos = base.indexOf('?')
        return if (pos == -1) base else base.substring(0, pos)
    }

    override fun onBind(intent: Intent): IBinder? {
        if (intent.action !in arrayOf(Intent.ACTION_SENDTO,
                TelephonyManager.ACTION_RESPOND_VIA_MESSAGE)) {
            return null
        }
        val extras = intent.extras ?: return null
        val message = extras.getString(Intent.EXTRA_TEXT) ?: extras.getString("sms_body")!!
        val intentUri: Uri = intent.data!!
        val recipients = getRecipients(intentUri)

        if (TextUtils.isEmpty(recipients) || TextUtils.isEmpty(message)) return null

        val number = extras.get(Intent.EXTRA_PHONE_NUMBER) as String?
        val adds = if (number == null) TextUtils.split(recipients, ";") else arrayOf(number)
        val conversations = Array(adds.size) {
            Conversation(
                null,
                adds[it],
                null,
                "",
                true,
                0,
                "",
                0,
                -1,
                FloatArray(5) { its ->
                    if (its == 0) 1f else 0f
                }
            )
        }
        SMSSender(this, conversations).sendSMS(message)
        return null
    }
}