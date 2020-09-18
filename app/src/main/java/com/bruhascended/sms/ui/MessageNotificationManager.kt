package com.bruhascended.sms.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.ml.getOtp

class MessageNotificationManager(private val mContext: Context) {
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

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        if (conversation.isMuted || conversation.sender == conversationSender || conversation.label == 5) return
        val yeah = Intent(mContext, ConversationActivity::class.java)
            .putExtra("ye", conversation)
        yeah.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            mContext, 0, yeah, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val otp = getOtp(message.text)
        val copyPI = PendingIntent.getBroadcast(mContext, 0, Intent("COPY"), 0)
        val deletePI = PendingIntent.getBroadcast(mContext, 0, Intent("DELETE"), 0)

        mContext.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied", Toast.LENGTH_LONG).show()
            }
        }, IntentFilter("COPY"))
        mContext.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val mdb = Room.databaseBuilder(
                    mContext, MessageDatabase::class.java, conversation.sender
                ).allowMainThreadQueries().build().manager()
                mdb.delete(message)
                if (mdb.loadAllSync().isEmpty()) {
                    if (isMainViewModelNull()) {
                        Room.databaseBuilder(
                            mContext, ConversationDatabase::class.java,
                            mContext.resources.getString(labelText[conversation.label])
                        ).allowMainThreadQueries().build().manager()
                    } else {
                        mainViewModel.daos[conversation.label]
                    }.delete(conversation)
                }
                NotificationManagerCompat.from(mContext).cancel(conversation.id!!.toInt())
                Toast.makeText(context, "Deleted", Toast.LENGTH_LONG).show()
                mContext.applicationContext.unregisterReceiver(this)
            }
        }, IntentFilter("DELETE"))

        val builder = when {
            message.path != null -> {
                NotificationCompat.Builder(mContext, conversation.label.toString())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                    .setContentTitle("Media from ${conversation.name ?: message.sender}")
            }
            otp == null -> {
                NotificationCompat.Builder(mContext, conversation.label.toString())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                    .setContentTitle(conversation.name ?: message.sender)
            }
            else -> {
                NotificationCompat.Builder(mContext, conversation.label.toString())
                    .setContentTitle("OTP from ${message.sender}")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(otp))
                    .addAction(R.drawable.ic_content_copy, mContext.getString(R.string.copy_otp), copyPI)
                    .addAction(R.drawable.ic_baseline_delete_24, mContext.getString(R.string.delete), deletePI)
            }
        }.setSmallIcon(R.drawable.message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(mContext).notify(conversation.id!!.toInt(), builder)
    }

    fun createNotificationChannel() {
        val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.notificationChannels.isEmpty()) {
            for (i in 0..4) {
                val name = mContext.getString(labelText[i])
                val channel = NotificationChannel(i.toString(), name, importance[i]).apply {
                    description = mContext.getString(descriptionText[i])
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
}