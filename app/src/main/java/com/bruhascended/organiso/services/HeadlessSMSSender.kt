package com.bruhascended.organiso.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.TelephonyManager
import android.text.TextUtils
import com.bruhascended.core.constants.EXTRA_MESSAGE_TEXT
import com.bruhascended.core.constants.EXTRA_NUMBER
import com.bruhascended.core.data.ContactsManager

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

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action !in arrayOf(Intent.ACTION_SENDTO,
                TelephonyManager.ACTION_RESPOND_VIA_MESSAGE)) {
            return super.onStartCommand(intent, flags, startId)
        }

        val cm = ContactsManager(applicationContext)
        val extras = intent.extras ?: return super.onStartCommand(intent, flags, startId)
        val message = extras.getString(Intent.EXTRA_TEXT) ?: extras.getString("sms_body")!!
        val intentUri: Uri = intent.data!!
        val recipients = getRecipients(intentUri)

        if (TextUtils.isEmpty(recipients) || TextUtils.isEmpty(message)) return super.onStartCommand(intent, flags, startId)

        val number = extras.get(Intent.EXTRA_PHONE_NUMBER) as String?
        val adds = if (number == null) TextUtils.split(recipients, ";") else arrayOf(number)
        adds.forEach {
            startService(
                Intent(this, SenderService::class.java).apply {
                    putExtra(EXTRA_NUMBER, cm.getClean(it))
                    putExtra(EXTRA_MESSAGE_TEXT, message)
                }
            )
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}