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
import androidx.room.Room
import androidx.work.*
import com.bruhascended.organiso.R
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.db.MessageDatabase
import com.bruhascended.organiso.mainViewModel
import com.bruhascended.organiso.requireMainViewModel
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
            val time = inputData.getLong("time", 0L)
            val sender = inputData.getString("sender")!!
            val id = inputData.getInt("id", 0)

            requireMainViewModel(mContext)
            val conversation = mainViewModel.daos[2].findBySender(sender).first()
            val mdb = Room.databaseBuilder(
                mContext, MessageDatabase::class.java, sender
            ).build()
            val message = mdb.manager().search(time).first()
            mdb.close()

            mContext.sendBroadcast(
                Intent(mContext, NotificationActionReceiver::class.java)
                    .setAction(ACTION_DELETE)
                    .putExtra("id", id)
                    .putExtra("show_toast", false)
                    .putExtra("message", message)
                    .putExtra("conversation", conversation)
            )
            return Result.success()
        }
    }


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
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        var text = "from ${conversation.sender}"
        if (prefs.getBoolean("copy_otp", true)) {
            text += " (Copied to Clipboard)"
            mContext.sendBroadcast(copyIntent)
        }

        if (prefs.getBoolean("delete_otp", false)) {
            val request = OneTimeWorkRequest.Builder(OtpDeleteWork::class.java)
                .setInitialDelay(DELAY_OTP_DELETE, TimeUnit.MINUTES)
                .setInputData(Data.Builder()
                    .putLong("time", message.time)
                    .putString("sender", conversation.sender)
                    .putInt("id", id)
                    .build()
                ).build()
            WorkManager.getInstance(mContext).enqueue(request)
        }

        val formattedOtp = "OTP: " + otp.slice(0 until otp.length/2) + " " +
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
                    R.drawable.ic_content_copy,
                    mContext.getString(R.string.copy_otp),
                    copyPI
                ).addAction(
                    R.drawable.ic_delete,
                    mContext.getString(R.string.delete),
                    deletePI
                ).setSmallIcon(R.drawable.message)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(notificationLayoutMin)
                .setCustomBigContentView(notificationLayout)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setExtras(Bundle().apply {
                    putBoolean("OTP", true)
                })
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}