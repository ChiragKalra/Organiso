package com.bruhascended.sms.services

import android.content.Context
import android.net.Uri
import com.bruhascended.sms.data.IncomingMMSManager
import com.bruhascended.sms.ui.MessageNotificationManager
import com.klinker.android.send_message.MmsReceivedReceiver


class MMSReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, uri: Uri) {
        MessageNotificationManager(context).sendSmsNotification(IncomingMMSManager(context).putMMS(uri))
    }

    override fun onError(p0: Context?, p1: String?) {}
}
