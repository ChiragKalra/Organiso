package com.bruhascended.sms.services

import android.content.Context
import android.net.Uri
import com.klinker.android.send_message.MmsReceivedReceiver


class MMSReceiver : MmsReceivedReceiver() {

    override fun onMessageReceived(context: Context, uri: Uri) {
        val k = 1
        val p = 1+k
    }

    override fun onError(p0: Context?, p1: String?) {
        TODO("Not yet implemented")
    }
}
