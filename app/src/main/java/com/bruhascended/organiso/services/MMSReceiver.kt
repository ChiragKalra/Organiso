package com.bruhascended.organiso.services

import android.content.Context
import android.net.Uri
import com.bruhascended.core.data.MMSManager
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationSender
import com.bruhascended.organiso.notifications.MessageNotificationManager
import com.klinker.android.send_message.MmsReceivedReceiver

class MMSReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, uri: Uri) {

        val out = MMSManager(context).putMMS(
            uri.toString().split("/").last(),
            init = false, activeSender = activeConversationSender
        )

        if (out != null) {
            MessageNotificationManager(context).sendSmsNotification(out)
        }
    }

    override fun onError(p0: Context?, p1: String?) {}
}
