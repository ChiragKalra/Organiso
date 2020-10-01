package com.bruhascended.sms.notifications

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.*
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.SMSManager.Companion.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.NotificationDatabase
import com.bruhascended.sms.ml.getOtp
import java.io.File
import com.bruhascended.sms.db.Notification as ActiveNotif

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

class MessageNotificationManager(
    private val mContext: Context
) {

    companion object {
        const val tableName = "active_notifications"

        const val ACTION_CANCEL = "NOTIFICATION_CANCELED"
        const val ACTION_COPY = "OTP_COPIED"
        const val ACTION_DELETE = "MESSAGE_DELETED"
    }

    private val defaultGroup = "MESSAGE_GROUP"
    private val summaryId = -1
    private val cm = ContactsManager(mContext)

    private val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)
    private val notificationManager = NotificationManagerCompat.from(mContext)
    private val ndb = Room.databaseBuilder(
        mContext, NotificationDatabase::class.java, tableName
    ).allowMainThreadQueries().build().manager()

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

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
    }

    private fun copyToClipboard(otp: String) {
        val clipboard =
            mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP", otp)
        clipboard.setPrimaryClip(clip)
        Handler(Looper.getMainLooper()).post{
            Toast.makeText(mContext, "OTP Copied To Clipboard", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("RestrictedApi")
    private fun getSenderIcon(conversation: Conversation): IconCompat? {
        return when {
            conversation.sender.first().isLetter() ->
                IconCompat.createFromIcon(Icon.createWithResource(mContext, R.drawable.ic_bot))
            conversation.name != null ->
                IconCompat.createFromIcon(Icon.createWithBitmap(
                        cm.retrieveContactPhoto(conversation.sender)))
            else ->
                IconCompat.createFromIcon(Icon.createWithResource(mContext, R.drawable.ic_person))
        }
    }

    private fun sendOtpNotif(otp: String, message: Message, conversation: Conversation, pendingIntent: PendingIntent) {
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val copyPI = PendingIntent.getBroadcast(mContext, 0,
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_COPY)
                .putExtra("otp", otp),
            0)
        val deletePI = PendingIntent.getBroadcast(mContext, 0,
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_DELETE)
                .putExtra("id", id)
                .putExtra("message", message)
                .putExtra("conversation", conversation),
            0)

        var text = "OTP from ${message.sender}"
        if (prefs.getBoolean("copy_otp", true)) {
            text += " (Copied to Clipboard)"
            copyToClipboard(otp)
        }
        NotificationCompat.Builder(mContext, conversation.label.toString())
            .setContentTitle(otp)
            .setContentText(text)
            .addAction(
                R.drawable.ic_content_copy,
                mContext.getString(R.string.copy_otp),
                copyPI
            ).addAction(
                R.drawable.ic_delete,
                mContext.getString(R.string.delete),
                deletePI
            ).setSmallIcon(R.drawable.message)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

    }

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        if (conversation.isMuted || conversation.sender == activeConversationSender || conversation.label == 5) return
        val yeah = Intent(mContext, ConversationActivity::class.java)
            .putExtra("ye", conversation)
        yeah.flags = Intent.FLAG_ACTIVITY_TASK_ON_HOME
        val contentPI = PendingIntent.getActivity(
            mContext, 0, yeah, PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelPI = PendingIntent.getBroadcast(
            mContext, 0,
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra("sender", conversation.sender),
            PendingIntent.FLAG_ONE_SHOT
        )

        val otp = getOtp(message.text)
        if (otp != null) {
            sendOtpNotif(otp, message, conversation, contentPI)
            return
        }

        ndb.insert(ActiveNotif(
            null,
            conversation.name ?: conversation.sender,
            message.text,
            System.currentTimeMillis(),
            conversation.label,
            message.path
        ))

        val sender = Person.Builder()
            .setName(conversation.name ?: conversation.sender)
            .setIcon(getSenderIcon(conversation))
            .setImportant(conversation.label == 0)
            .build()

        val convStyle = NotificationCompat.MessagingStyle(sender)
        ndb.findBySender(conversation.sender).forEach {
            var msgText = ""
            if (it.path != null) {
                val mtype = getMimeType(it.path!!)
                msgText = when {
                    mtype.startsWith("audio") -> "Audio: "
                    mtype.startsWith("video") -> "Video: "
                    else -> ""
                } + it.text
            }
            val msg = NotificationCompat.MessagingStyle.Message(
                msgText,
                it.time,
                sender
            )
            if (it.path != null && getMimeType(it.path!!).startsWith("photo")) {
                msg.setData(getMimeType(it.path!!), Uri.fromFile(File(it.path!!)))
            }
            convStyle.addMessage(msg)
        }

        val notif = NotificationCompat.Builder(mContext, conversation.label.toString())
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setGroup(conversation.label.toString())
            .setSmallIcon(R.drawable.message)
            .setContentIntent(contentPI)
            .setDeleteIntent(cancelPI)
            .setAutoCancel(true)
            .setStyle(convStyle)

        notificationManager.notify(conversation.id!!.toInt(), notif.build())

        val set = hashSetOf<String>()
        ndb.loadAllSync().forEach { n ->
            if (notificationManager.getNotificationChannel(n.label.toString())?.importance
                != NotificationManager.IMPORTANCE_NONE)
                set.add(n.sender)
        }
        if (set.size < 3) return
        val summaryNotification = NotificationCompat.Builder(mContext, "0")
            .setSmallIcon(R.drawable.message)
            .setGroup(defaultGroup)
            .setAutoCancel(true)
            .setGroupSummary(true)
            .build()
        notificationManager.notify(0, summaryNotification)
    }

    fun createNotificationChannel() {
        val notificationManager =
            mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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