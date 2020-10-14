package com.bruhascended.organiso.notifications

import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.room.Room
import com.bruhascended.organiso.activeConversationDao
import com.bruhascended.organiso.activeConversationSender
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.db.MessageDatabase
import com.bruhascended.organiso.db.NotificationDatabase
import com.bruhascended.organiso.mainViewModel
import com.bruhascended.organiso.requireMainViewModel
import com.bruhascended.organiso.services.SMSSender

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
                    Toast.makeText(mContext, "OTP Copied To Clipboard", Toast.LENGTH_LONG).show()
                }
            }
            ACTION_DELETE -> {
                val id = intent.getIntExtra("id", 0)
                val toast = intent.getBooleanExtra("show_toast", true)
                val message = intent.getSerializableExtra("message") as Message
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                val mdb = if (activeConversationSender != conversation.sender) Room.databaseBuilder(
                    mContext, MessageDatabase::class.java, conversation.sender
                ).allowMainThreadQueries().build().manager() else activeConversationDao
                mdb.delete(message)
                if (mdb.loadLastSync() == null) {
                    requireMainViewModel(mContext)
                    mainViewModel.daos[conversation.label].delete(conversation)
                }
                if (toast) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                    }
                }
                NotificationManagerCompat.from(mContext).cancel(id)
            }
            ACTION_REPLY -> {
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                val replyText = RemoteInput.getResultsFromIntent(intent).getCharSequence(KEY_TEXT_REPLY).toString()

                val newMessage = Message(
                    null,
                    conversation.sender,
                    replyText,
                    0,
                    System.currentTimeMillis(),
                    0
                )
                MessageNotificationManager(mContext).sendSmsNotification(newMessage to conversation)

                /*mContext.applicationContext.startService(
                    Intent(mContext, HeadlessSMSSender::class.java).apply {
                        action = Intent.ACTION_SENDTO
                        putExtra(Intent.EXTRA_TEXT, replyText)
                        putExtra(Intent.EXTRA_PHONE_NUMBER, conversation.sender)
                    }
                )*/
                SMSSender(mContext.applicationContext, arrayOf(conversation))
                    .sendSMS(replyText)
            }
        }
    }
}