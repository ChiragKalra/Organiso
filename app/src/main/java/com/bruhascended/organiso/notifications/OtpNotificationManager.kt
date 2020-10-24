package com.bruhascended.organiso.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.work.*
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.R
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.data.MainDaoProvider
import java.util.concurrent.TimeUnit

class OtpNotificationManager (
    private val mContext: Context
) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
    private val notificationManager = NotificationManagerCompat.from(mContext)

    class OtpDeleteWork(
        private val mContext: Context,
        workerParams: WorkerParameters
    ): Worker(mContext, workerParams) {

        override fun doWork(): Result {
            val time = inputData.getLong(EXTRA_TIME, 0L)
            val sender = inputData.getString(EXTRA_SENDER)!!
            val id = inputData.getInt(EXTRA_NOTIFICATION_ID, 0)

            val conversation = MainDaoProvider(mContext)
                .getMainDaos()[LABEL_TRANSACTIONS].findBySender(sender).first()
            val mdb = MessageDbFactory(mContext).of(sender)
            val message = mdb.manager().search(time)
            mdb.close()

            mContext.sendBroadcast(
                Intent(mContext, NotificationActionReceiver::class.java)
                    .setAction(ACTION_DELETE_OTP)
                    .putExtra(EXTRA_NOTIFICATION_ID, id)
                    .putExtra(EXTRA_MESSAGE, message)
                    .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString())
            )
            return Result.success()
        }
    }


    fun sendOtpNotif(otp: String, message: Message, conversation: Conversation, pendingIntent: PendingIntent) {
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val copyIntent = Intent(mContext, NotificationActionReceiver::class.java)
            .setAction(ACTION_COPY)
            .putExtra(EXTRA_OTP, otp)
        val copyPI = PendingIntent.getBroadcast(mContext, conversation.id!!,
            copyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val deletePI = PendingIntent.getBroadcast(mContext, conversation.id!!,
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_DELETE_OTP)
                .putExtra(EXTRA_NOTIFICATION_ID, id)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        var text = mContext.getString(R.string.from_sender, conversation.address)
        if (prefs.getBoolean(PREF_COPY_OTP, true)) {
            text += mContext.getString(R.string.copied_in_brackets)
            mContext.sendBroadcast(copyIntent)
        }

        if (prefs.getBoolean(PREF_DELETE_OTP, false)) {
            val request = OneTimeWorkRequest.Builder(OtpDeleteWork::class.java)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .setInputData(Data.Builder()
                    .putLong(EXTRA_TIME, message.time)
                    .putString(EXTRA_SENDER, conversation.clean)
                    .putInt(EXTRA_NOTIFICATION_ID, id)
                    .build()
                ).build()
            WorkManager.getInstance(mContext).enqueue(request)
        }

        val formattedOtp = mContext.getString(R.string.otp_col) +
                otp.slice(0 until otp.length/2) + " " +
                otp.slice(otp.length/2 until otp.length)
        val notificationLayout = RemoteViews(mContext.packageName,
            R.layout.view_notification_otp).apply{
            setTextViewText(android.R.id.content, text)
            setTextViewText(android.R.id.title, formattedOtp)
        }
        val notificationLayoutMin = RemoteViews(mContext.packageName,
            R.layout.view_notification_otp_small).apply{
            setTextViewText(android.R.id.title, formattedOtp)
        }

        notificationManager.notify(id,
            NotificationCompat.Builder(mContext, conversation.label.toString())
                .addAction(
                    R.drawable.ic_copy_notif,
                    mContext.getString(R.string.copy_otp),
                    copyPI
                ).addAction(
                    R.drawable.ic_delete_notif,
                    mContext.getString(R.string.delete),
                    deletePI
                ).setSmallIcon(R.drawable.message)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayoutMin)
                .setCustomBigContentView(notificationLayout)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(false)
                .setColorized(true)
                .setColor(mContext.getColor(R.color.colorAccent))
                .setExtras(Bundle().apply {
                    putBoolean(EXTRA_IS_OTP, true)
                })
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}