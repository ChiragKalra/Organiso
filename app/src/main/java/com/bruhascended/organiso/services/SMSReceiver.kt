package com.bruhascended.organiso.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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

    override fun onReceive(context: Context, intent: Intent) {
        context.startService(
            Intent(context, SMSReceiverService::class.java).apply {
                action = intent.action
                putExtra("pdus", intent.extras!!["pdus"] as Array<*>)
                putExtra("format", intent.extras!!["format"] as String)
            }
        )
    }
}
