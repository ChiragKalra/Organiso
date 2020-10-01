package com.bruhascended.sms.notifications

import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.db.NotificationDatabase
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.notifications.MessageNotificationManager.Companion.ACTION_CANCEL
import com.bruhascended.sms.notifications.MessageNotificationManager.Companion.ACTION_COPY
import com.bruhascended.sms.notifications.MessageNotificationManager.Companion.ACTION_DELETE
import com.bruhascended.sms.notifications.MessageNotificationManager.Companion.tableName
import com.bruhascended.sms.requireMainViewModel

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(mContext: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CANCEL -> {
                val sender = intent.getStringExtra("sender") ?: return
                Room.databaseBuilder(
                    mContext, NotificationDatabase::class.java, tableName
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
        }
    }
}