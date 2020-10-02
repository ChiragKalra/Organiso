package com.bruhascended.sms.notifications

import android.annotation.SuppressLint
import android.app.Notification.CATEGORY_MESSAGE
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import androidx.room.Room
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.activeConversationSender
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.NotificationDatabase
import com.bruhascended.sms.ml.getOtp
import java.io.File
import com.bruhascended.sms.db.Notification

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

const val NAME_TABLE = "active_notifications"
const val ACTION_CANCEL = "NOTIFICATION_CANCELED"
const val ACTION_REPLY = "NOTIFICATION_REPLIED"
const val ACTION_COPY = "OTP_COPIED"
const val ACTION_DELETE = "MESSAGE_DELETED"
const val GROUP_DEFAULT = "MESSAGE_GROUP"
const val ID_SUMMARY = -1221
const val KEY_TEXT_REPLY = "key_text_reply"

@SuppressLint("RestrictedApi")
class MessageNotificationManager(
    private val mContext: Context
) {
    private val cm = ContactsManager(mContext)
    private val onm = OtpNotificationManager(mContext)
    private val notificationManager = NotificationManagerCompat.from(mContext)

    private val ndb = Room.databaseBuilder(
        mContext, NotificationDatabase::class.java, NAME_TABLE
    ).allowMainThreadQueries().build().manager()

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
    }

    private fun getSenderIcon(conversation: Conversation): IconCompat? {
        return when {
            conversation.sender.first().isLetter() -> IconCompat.createFromIcon(
                Icon.createWithResource(mContext, R.drawable.ic_bot))
            conversation.name != null -> IconCompat.createFromIcon(Icon.createWithBitmap(
                cm.retrieveContactPhoto(conversation.sender)))
            else -> IconCompat.createFromIcon(Icon.createWithResource(mContext, R.drawable.ic_person))
        }
    }

    private fun geUserIcon() = IconCompat.createFromIcon(
        Icon.createWithResource(mContext, R.drawable.ic_person)
    )


    private fun showSummaryNotification() {
        val set = hashSetOf<String>()
        ndb.loadAllSync().forEach { n ->
            if (notificationManager.getNotificationChannel(n.label.toString())?.importance
                != IMPORTANCE_NONE) set.add(n.sender)
        }
        if (set.size < 3) return
        notificationManager.notify(0,
            Builder(mContext, "0")
            .setSmallIcon(R.drawable.message)
            .setAutoCancel(true)
            .setGroup(GROUP_DEFAULT)
            .setGroupSummary(true)
            .build()
        )
    }

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        if (conversation.isMuted || conversation.sender == activeConversationSender ||
            conversation.label == 5) return

        val contentPI = PendingIntent.getActivity(
            mContext, conversation.id!!.toInt(),
            Intent(mContext, ConversationActivity::class.java)
                .putExtra("ye", conversation)
                .setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME),
            PendingIntent.FLAG_ONE_SHOT
        )

        val otp = getOtp(message.text)
        if (otp != null) {
            onm.sendOtpNotif(otp, message, conversation, contentPI)
            return
        }

        val senderPerson = Person.Builder()
            .setName(conversation.name ?: conversation.sender)
            .setIcon(getSenderIcon(conversation))
            .build()

        val userPerson = Person.Builder()
            .setName("You")
            .setIcon(getSenderIcon(conversation))
            .build()

        val cancelPI = PendingIntent.getBroadcast(
            mContext, conversation.id!!.toInt(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra("sender", conversation.sender),
            PendingIntent.FLAG_ONE_SHOT
        )
        ndb.insert(Notification(
            conversation.name ?: conversation.sender,
            message.text,
            System.currentTimeMillis(),
            conversation.label,
            message.path,
            message.type != 1
        ))

        val conversationStyle = NotificationCompat.MessagingStyle(senderPerson)
        ndb.findBySender(conversation.sender).forEach {
            var msgText = ""
            if (it.path != null) {
                val mType = getMimeType(it.path!!)
                msgText = when {
                    mType.startsWith("audio") -> "Audio: "
                    mType.startsWith("video") -> "Video: "
                    else -> ""
                } + it.text
            }
            val msg = NotificationCompat.MessagingStyle.Message(
                msgText,
                it.time,
                if (it.fromUser) userPerson else senderPerson
            )
            if (it.path != null && getMimeType(it.path!!).startsWith("photo")) {
                msg.setData(getMimeType(it.path!!), Uri.fromFile(File(it.path!!)))
            }
            conversationStyle.addMessage(msg)
        }

        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel("Reply")
            build()
        }
        val replyPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            mContext.applicationContext,
            conversation.id!!.toInt(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra("conversation", conversation),
            PendingIntent.FLAG_UPDATE_CURRENT)
        val action: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, "Reply" , replyPendingIntent)
                .addRemoteInput(remoteInput).build()

        val notification = Builder(mContext, conversation.label.toString())
            .setCategory(CATEGORY_MESSAGE)
            .setGroup(GROUP_DEFAULT)
            .setSmallIcon(R.drawable.message)
            .setContentIntent(contentPI)
            .setDeleteIntent(cancelPI)
            .setAutoCancel(true)
            .setStyle(conversationStyle)

        if (conversation.sender.first().isDigit()) notification.addAction(action)

        notificationManager.notify(conversation.id!!.toInt(), notification.build())
        showSummaryNotification()
    }
}