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

import android.content.Context
import android.net.Uri
import com.bruhascended.sms.data.MMSManager
import com.bruhascended.sms.ui.MessageNotificationManager
import com.klinker.android.send_message.MmsReceivedReceiver


class MMSReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, uri: Uri) {
        MessageNotificationManager(context).sendSmsNotification(MMSManager(context)
                .putMMS(uri.toString().split("/").last())!!)
    }

    override fun onError(p0: Context?, p1: String?) {}
}
