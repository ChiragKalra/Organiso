package com.bruhascended.sms.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message

class OtpNotificationManager (
    private val mContext: Context
) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)

    fun sendOtpNotif(otp: String, message: Message, conversation: Conversation, pendingIntent: PendingIntent) {
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val copyIntent = Intent(mContext, NotificationActionReceiver::class.java)
            .setAction(ACTION_COPY)
            .putExtra("otp", otp)
        val copyPI = PendingIntent.getBroadcast(mContext, conversation.id!!.toInt(),
            copyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val deletePI = PendingIntent.getBroadcast(mContext, conversation.id!!.toInt(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_DELETE)
                .putExtra("id", id)
                .putExtra("message", message)
                .putExtra("conversation", conversation),
            PendingIntent.FLAG_ONE_SHOT
        )

        var text = "OTP from ${message.sender}"
        if (prefs.getBoolean("copy_otp", true)) {
            text += " (Copied to Clipboard)"
            mContext.sendBroadcast(copyIntent)
        }

        val notificationLayout = RemoteViews(mContext.packageName, R.layout.view_notification_otp)

        NotificationCompat.Builder(mContext, conversation.label.toString())
            .setContentTitle(text)
            .addAction(
                R.drawable.ic_content_copy,
                mContext.getString(R.string.copy_otp),
                copyPI
            ).addAction(
                R.drawable.ic_delete,
                mContext.getString(R.string.delete),
                deletePI
            ).setSmallIcon(R.drawable.message)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(notificationLayout)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

    }
}