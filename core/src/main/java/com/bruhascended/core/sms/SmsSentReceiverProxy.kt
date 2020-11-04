package com.bruhascended.core.sms

import android.content.Context
import android.content.Intent
import com.klinker.android.send_message.SentReceiver


class SmsSentReceiverProxy : SentReceiver() {
    /*
     * (non-Javadoc)
     * @see com.klinker.android.send_message.StatusUpdatedReceiver#onMessageStatusUpdated(android.content.Context, android.content.Intent, int)
     */
    override fun onMessageStatusUpdated(context: Context, intent: Intent, receiverResultCode: Int) {}
}