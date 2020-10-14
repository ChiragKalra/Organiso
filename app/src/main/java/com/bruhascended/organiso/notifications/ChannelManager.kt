package com.bruhascended.organiso.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.bruhascended.organiso.R
import com.bruhascended.organiso.data.SMSManager

class ChannelManager(
    private val mContext: Context
) {

    private val descriptionText = arrayOf(
        R.string.text_1,
        R.string.text_2,
        R.string.text_3,
        R.string.text_4,
        R.string.text_5
    )

    private val importance = arrayOf(
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_NONE,
        NotificationManager.IMPORTANCE_NONE
    )

    fun createNotificationChannels() {
        val notificationManager =
            mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.notificationChannels.isEmpty()) {
            for (i in 0..4) {
                val name = mContext.getString(SMSManager.labelText[i])
                val channel = NotificationChannel(i.toString(), name, importance[i]).apply {
                    description = mContext.getString(descriptionText[i])
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}