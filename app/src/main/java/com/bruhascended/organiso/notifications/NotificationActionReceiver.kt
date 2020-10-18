package com.bruhascended.organiso.notifications

import android.content.*
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.core.data.ContactsManager.Companion.EXTRA_SENDER
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.data.SMSManager.Companion.EXTRA_MESSAGE
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_SENT
import com.bruhascended.organiso.*
import com.bruhascended.core.db.*
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_CANCEL
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_COPY
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_REPLY
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.KEY_TEXT_REPLY
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.core.db.MainDaoProvider
import com.bruhascended.organiso.ConversationActivity.Companion.EXTRA_CONVERSATION_JSON
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_DELETE_MESSAGE
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_DELETE_OTP
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_MARK_READ
import com.bruhascended.organiso.notifications.MessageNotificationManager.Companion.ACTION_REPORT_SPAM

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_OTP = "otp"
        const val EXTRA_NOTIFICATION_ID = "notif_id"

        fun Context.cancelNotification(sender: String, id: Long?) {
            NotificationManagerCompat.from(this).cancel(id!!.toInt())
            sendBroadcast(
                Intent(this, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_SENDER, sender)
            )
        }

    }

    private lateinit var mContext: Context
    private lateinit var mIntent: Intent

    override fun onReceive(context: Context, intent: Intent) {
        mContext = context.applicationContext
        mIntent = intent

        when (intent.action) {
            ACTION_CANCEL -> {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return
                NotificationDbFactory(mContext).get().apply {
                    manager().findBySender(sender).forEach {
                        manager().delete(it)
                    }
                    close()
                }
            }
            ACTION_COPY -> {
                val otp = intent.getStringExtra(EXTRA_OTP) ?: return
                val clipboard =
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OTP", otp)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post{
                    Toast.makeText(mContext, mContext.getString(R.string.otp_copied), Toast.LENGTH_LONG).show()
                }
            }
            ACTION_DELETE_OTP, ACTION_DELETE_MESSAGE -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                val message = intent.getSerializableExtra(EXTRA_MESSAGE) as Message
                MessageDbFactory(mContext).of(conversation.clean).apply {
                    manager().delete(message)
                    val last = manager().loadLastSync()
                    if (last == null) {
                        MainDaoProvider(mContext).getMainDaos()[conversation.label]
                            .delete(conversation)
                    } else {
                        conversation.apply {
                            read = true
                            lastMMS = last.path != null
                            lastSMS = last.text
                            time = last.time
                            MainDaoProvider(mContext).getMainDaos()[label].update(this)
                        }
                    }
                    close()
                }
                val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                if (id == -1) {
                    mContext.cancelNotification(conversation.clean, conversation.id)
                } else {
                    NotificationManagerCompat.from(mContext).cancel(id)
                }
            }
            ACTION_MARK_READ -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                conversation.read = true
                MainDaoProvider(mContext).getMainDaos()[conversation.label].update(conversation)
                mContext.cancelNotification(conversation.clean, conversation.id)
            }
            ACTION_REPLY -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                val replyText = RemoteInput.getResultsFromIntent(intent).getCharSequence(KEY_TEXT_REPLY).toString()

                val newMessage = Message(replyText, MESSAGE_TYPE_SENT, System.currentTimeMillis())
                MessageNotificationManager(mContext).sendSmsNotification(newMessage to conversation)

                SMSSender(mContext.applicationContext, arrayOf(conversation)).sendSMS(replyText)
            }
            ACTION_REPORT_SPAM -> {
                val conversation =
                    intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
                AnalyticsLogger(mContext).apply {
                    log("${conversation.label}_to_4")
                    reportSpam(conversation)
                }
                mContext.cancelNotification(conversation.clean, conversation.id)
                conversation.moveTo(SMSManager.LABEL_SPAM, mContext)
            }
        }
    }
}