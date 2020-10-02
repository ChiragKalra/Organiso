package com.bruhascended.sms.notifications

import android.content.*
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.db.NotificationDatabase
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.requireMainViewModel
import com.bruhascended.sms.services.HeadlessSMSSender

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
                val message = intent.getSerializableExtra("message") as Message
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                val mdb = Room.databaseBuilder(
                    mContext, MessageDatabase::class.java, conversation.sender
                ).allowMainThreadQueries().build().manager()
                mdb.delete(message)
                if (mdb.loadAllSync().isEmpty()) {
                    requireMainViewModel(mContext)
                    mainViewModel.daos[conversation.label].delete(conversation)
                }
                Handler(Looper.getMainLooper()).post{
                    Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                }
                NotificationManagerCompat.from(mContext).cancel(id)
            }
            ACTION_REPLY -> {
                val message = intent.getSerializableExtra("message") as Message
                val conversation = intent.getSerializableExtra("conversation") as Conversation
                val replyText = RemoteInput.getResultsFromIntent(intent).getCharSequence(KEY_TEXT_REPLY).toString()
                mContext.startService(
                    Intent(mContext, HeadlessSMSSender::class.java)
                        .setAction(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE)
                        .setData(Uri.parse("smsto:${conversation.sender}"))
                        .putExtra(Intent.EXTRA_TEXT, replyText)
                )
                val newMessage = Message(
                    null,
                    message.sender,
                    replyText,
                    0,
                    System.currentTimeMillis(),
                    0
                )
                MessageNotificationManager(mContext).sendSmsNotification(newMessage to conversation)
            }
        }
    }
}