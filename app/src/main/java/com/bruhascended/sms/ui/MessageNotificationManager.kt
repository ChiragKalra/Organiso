package com.bruhascended.sms.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.data.labelText
import com.bruhascended.db.Conversation
import com.bruhascended.db.Message

class MessageNotificationManager (private val mContext: Context) {
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

    fun sendSmsNotification(conversation: Conversation, message: Message) {
        if (conversation.isMuted) return
        val yeah = Intent(mContext, ConversationActivity::class.java)
            .putExtra("ye", conversation)
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            mContext, 0, yeah, PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (conversation.label != 5) {
            val builder = NotificationCompat.Builder(mContext, conversation.label.toString())
                .setSmallIcon(R.mipmap.ic_launcher_foreground)
                .setContentTitle(conversation.name?: message.sender)
                .setContentText(message.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            with(NotificationManagerCompat.from(mContext)) {
                notify(conversation.id!!.toInt(), builder.build())
            }
        }
    }

    fun createNotificationChannel() {
        val notificationManager: NotificationManager =
            mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.notificationChannels.isEmpty()) {
            for (i in 0..4) {
                val name = mContext.getString(labelText[i])
                val descriptionText = mContext.getString(descriptionText[i])
                val channel = NotificationChannel(i.toString(), name, importance[i]).apply {
                    description = descriptionText
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}