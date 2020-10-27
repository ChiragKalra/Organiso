package com.bruhascended.organiso.notifications

import android.app.Notification.CATEGORY_MESSAGE
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.view.Gravity
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Builder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.bruhascended.organiso.ConversationActivity
import com.bruhascended.organiso.R
import com.bruhascended.core.data.ContactsManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.db.*
import com.bruhascended.core.model.getOtp
import java.io.File

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

    private val defaultGroup = "MESSAGE_GROUP"

    private val cm = ContactsManager(mContext)
    private val onm = OtpNotificationManager(mContext)
    private val colorRes = mContext.resources.getIntArray(R.array.colors)
    private val notificationManager = NotificationManagerCompat.from(mContext)

    private val ndb = NotificationDbFactory(mContext).get().manager()

    private fun getMimeType(url: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width, bitmap
                .height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        val roundPx = bitmap.width.toFloat()
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun getSenderIcon(conversation: Conversation): IconCompat {
        val dp = File(mContext.filesDir, conversation.number)
        val bg = ContextCompat.getDrawable(mContext, R.drawable.bg_notification_icon)?.apply {
            setTint(colorRes[(conversation.hashCode() % colorRes.size)])
        }

        return when {
            conversation.isBot -> {
                val bot = ContextCompat.getDrawable(mContext, R.drawable.ic_bot)
                val finalDrawable = LayerDrawable(arrayOf(bg, bot))
                finalDrawable.setLayerGravity(1, Gravity.CENTER)
                IconCompat.createWithBitmap(finalDrawable.toBitmap())
            }
            dp.exists() -> {
                val bm = getRoundedCornerBitmap(BitmapFactory.decodeFile(dp.absolutePath))
                IconCompat.createWithBitmap(bm)
            }
            else -> {
                val person = ContextCompat.getDrawable(mContext, R.drawable.ic_person)
                val finalDrawable = LayerDrawable(arrayOf(bg, person))
                finalDrawable.setLayerGravity(1, Gravity.CENTER)
                IconCompat.createWithBitmap(finalDrawable.toBitmap())
            }
        }
    }

    private fun getUserIcon(): IconCompat {
        val bg = ContextCompat.getDrawable(mContext, R.drawable.bg_notification_icon)?.apply {
            setTint(colorRes.first())
        }

        val person = ContextCompat.getDrawable(mContext, R.drawable.ic_person)
        val finalDrawable = LayerDrawable(arrayOf(bg, person))
        finalDrawable.setLayerGravity(1, Gravity.CENTER)
        return IconCompat.createWithBitmap(finalDrawable.toBitmap())
    }

    private fun showSummaryNotification() {
        val mNM = mContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        var active = 0
        mNM.activeNotifications.forEach {
            if (!it.notification.extras.getBoolean(EXTRA_IS_OTP, false)) active++
        }
        if (active < 3) return
        notificationManager.notify(
            0,
            Builder(mContext, "0")
                .setSmallIcon(R.drawable.message)
                .setAutoCancel(true)
                .setGroup(defaultGroup)
                .setGroupSummary(true)
                .setNotificationSilent()
                .build()
        )
    }

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        if (conversation.isMuted || conversation.label == LABEL_BLOCKED) {
            return
        }

        val contentPI = PendingIntent.getActivity(
            mContext, conversation.hashCode(),
            Intent(mContext, ConversationActivity::class.java)
                .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString())
                .setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME),
            FLAG_UPDATE_CURRENT
        )

        val otp = getOtp(message.text)
        if (otp != null) {
            onm.sendOtpNotif(otp, message, conversation, contentPI)
            return
        }

        val senderPerson = Person.Builder()
            .setName(
                ContactsProvider(mContext).getNameOrNull(conversation.number)
                    ?: conversation.number
            )
            .setIcon(getSenderIcon(conversation))
            .build()

        val userPerson = Person.Builder()
            .setName(mContext.getString(R.string.you))
            .setIcon(getUserIcon())
            .build()

        val cancelPI = PendingIntent.getBroadcast(
            mContext, conversation.hashCode(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_NUMBER, conversation.number),
            FLAG_UPDATE_CURRENT
        )

        if (notificationManager.getNotificationChannel(conversation.label.toString())?.importance
            != IMPORTANCE_NONE
        ) {
            ndb.insert(
                Notification(
                    conversation.number,
                    message.text,
                    System.currentTimeMillis(),
                    conversation.label,
                    message.path,
                    message.type != MESSAGE_TYPE_INBOX
                )
            )
        }

        val conversationStyle = NotificationCompat.MessagingStyle(senderPerson)
        ndb.findBySender(conversation.number).forEach {
            var msgText = it.text
            if (it.path != null) {
                val mType = getMimeType(it.path!!)
                msgText = when {
                    mType.startsWith("audio") -> mContext.getString(R.string.audio_col)
                    mType.startsWith("video") -> mContext.getString(R.string.video_col)
                    else -> ""
                } + it.text
            }
            val msg = NotificationCompat.MessagingStyle.Message(
                msgText,
                it.time,
                if (it.fromUser) userPerson else senderPerson
            )
            if (it.path != null && getMimeType(it.path!!).startsWith("image")) {
                msg.setData(getMimeType(it.path!!), Uri.fromFile(File(it.path!!)))
            }
            conversationStyle.addMessage(msg)
        }

        val remoteInput: RemoteInput = RemoteInput.Builder(EXTRA_TEXT_REPLY).run {
            setLabel(mContext.getString(R.string.reply))
            build()
        }
        val replyPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            mContext.applicationContext,
            conversation.hashCode(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString()),
            FLAG_UPDATE_CURRENT
        )
        val replyAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, mContext.getString(R.string.reply), replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val readPI = PendingIntent.getBroadcast(mContext, conversation.hashCode(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_MARK_READ)
                .putExtra(EXTRA_NUMBER, conversation.number)
                .putExtra(EXTRA_LABEL, conversation.label),
            FLAG_UPDATE_CURRENT
        )
        val deletePI = PendingIntent.getBroadcast(mContext, conversation.hashCode(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_DELETE_MESSAGE)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString()),
            FLAG_UPDATE_CURRENT
        )
        val reportSpamPI = PendingIntent.getBroadcast(mContext, conversation.hashCode(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPORT_SPAM)
                .putExtra(EXTRA_CONVERSATION_JSON, conversation.toString()),
            FLAG_UPDATE_CURRENT
        )

        val context = mContext
        val notification = Builder(mContext, conversation.label.toString())
            .setCategory(CATEGORY_MESSAGE)
            .setGroup(defaultGroup)
            .setColorized(true)
            .setColor(mContext.getColor(R.color.colorAccent))
            .setSmallIcon(R.drawable.message)
            .setContentIntent(contentPI)
            .setAutoCancel(false)
            .setStyle(conversationStyle).apply {
                if (message.type != MESSAGE_TYPE_INBOX) setNotificationSilent()
                if (!conversation.isBot && conversation.label == LABEL_PERSONAL) {
                    addAction(replyAction)
                } else {
                    addAction(
                        R.drawable.ic_report_notif,
                        context.getString(R.string.report_spam),
                        reportSpamPI
                    )
                }
            }.addAction(
                R.drawable.ic_mark_chat_read,
                mContext.getString(R.string.mark_as_read),
                readPI
            ).addAction(
                R.drawable.ic_delete_notif,
                mContext.getString(R.string.delete),
                deletePI
            ).setDeleteIntent(cancelPI)

        notificationManager.notify(conversation.hashCode(), notification.build())
        showSummaryNotification()
    }
}