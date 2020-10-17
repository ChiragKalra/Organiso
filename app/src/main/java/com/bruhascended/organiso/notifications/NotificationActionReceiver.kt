package com.bruhascended.organiso.notifications

import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.room.Room
import com.bruhascended.organiso.*
import com.bruhascended.organiso.db.*
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_CANCEL
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_COPY
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_DELETE
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_REPLY
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.KEY_TEXT_REPLY
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.NAME_TABLE
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.db.MainDaoProvider

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(mContext: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL -> {
                val sender = intent.getStringExtra("sender") ?: return
                Room.databaseBuilder(
                    mContext, NotificationDatabase::class.java, NAME_TABLE
                ).allowMainThreadQueries().build().apply {
                    manager().findBySender(sender).forEach {
                        manager().delete(it)
                    }
                    close()
                }
            }
            ACTION_COPY -> {
                val otp = intent.getStringExtra("otp") ?: return
                val clipboard =
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post{
                    Toast.makeText(mContext, mContext.getString(R.string.otp_copied), Toast.LENGTH_LONG).show()
                }
            }
            ACTION_DELETE -> {
                val id = intent.getIntExtra("id", 0)
                val toast = intent.getBooleanExtra("show_toast", true)
                val message = intent.getSerializableExtra("message") as Message
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                MessageDbFactory(mContext).of(conversation.sender).apply {
                    manager().delete(message)
                    if (manager().loadLastSync() == null) {
                        MainDaoProvider(mContext).getMainDaos()[conversation.label].delete(conversation)
                    }
                    close()
                }
                if (toast) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(mContext, mContext.getString(R.string.deleted), Toast.LENGTH_LONG).show()
                    }
                }
                NotificationManagerCompat.from(mContext).cancel(id)
            }
            ACTION_REPLY -> {
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                val replyText = RemoteInput.getResultsFromIntent(intent).getCharSequence(KEY_TEXT_REPLY).toString()

                val newMessage = Message(replyText, 0, System.currentTimeMillis())
                MessageNotificationManager(mContext).sendSmsNotification(newMessage to conversation)

                SMSSender(mContext.applicationContext, arrayOf(conversation)).sendSMS(replyText)
            }
        }
    }
}