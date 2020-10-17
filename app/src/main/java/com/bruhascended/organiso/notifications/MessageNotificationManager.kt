package com.bruhascended.organiso.notifications

import android.app.Notification.CATEGORY_MESSAGE
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_NONE
import android.app.PendingIntent
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
import androidx.room.Room
import com.bruhascended.organiso.ConversationActivity
import com.bruhascended.organiso.ConversationActivity.Companion.EXTRA_SENDER
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationSender
import com.bruhascended.organiso.R
import com.bruhascended.core.data.ContactsManager
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.Notification
import com.bruhascended.core.db.NotificationDatabase
import com.bruhascended.core.ml.getOtp
import com.bruhascended.organiso.ui.main.ConversationRecyclerAdaptor.Companion.colorRes
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

    companion object {
        const val NAME_TABLE = "active_notifications"
        const val ACTION_CANCEL = "NOTIFICATION_CANCELED"
        const val ACTION_REPLY = "NOTIFICATION_REPLIED"
        const val ACTION_COPY = "OTP_COPIED"
        const val ACTION_DELETE = "MESSAGE_DELETED"
        const val GROUP_DEFAULT = "MESSAGE_GROUP"
        const val ID_SUMMARY = -1221
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val DELAY_OTP_DELETE = 15L // in minutes
    }

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
        val dp = File(mContext.filesDir, conversation.sender)
        val bg = ContextCompat.getDrawable(mContext, R.drawable.bg_notification_icon)?.apply {
            setTint(mContext.getColor(colorRes[(conversation.id!! % colorRes.size).toInt()]))
        }

        return when {
            conversation.sender.first().isLetter() -> {
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
            setTint(mContext.getColor(colorRes.first()))
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
            if (!it.notification.extras.getBoolean("OTP", false)) active++
        }
        if (active < 3) return
        notificationManager.notify(
            0,
            Builder(mContext, "0")
                .setContentTitle(mContext.getString(R.string.x_active_conversations, active))
                .setSmallIcon(R.drawable.message)
                .setAutoCancel(true)
                .setGroup(GROUP_DEFAULT)
                .setGroupSummary(true)
                .setNotificationSilent()
                .build()
        )
    }

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        if (conversation.isMuted || conversation.sender == activeConversationSender ||
            conversation.label == 5
        ) return

        val contentPI = PendingIntent.getActivity(
            mContext, conversation.id!!.toInt(),
            Intent(mContext, ConversationActivity::class.java)
                .putExtra(EXTRA_SENDER, conversation.sender)
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
            .setName(mContext.getString(R.string.you))
            .setIcon(getUserIcon())
            .build()

        val cancelPI = PendingIntent.getBroadcast(
            mContext, conversation.id!!.toInt(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra("sender", conversation.sender),
            PendingIntent.FLAG_ONE_SHOT
        )

        if (notificationManager.getNotificationChannel(conversation.label.toString())?.importance
            != IMPORTANCE_NONE
        ) {
            ndb.insert(
                Notification(
                    conversation.sender,
                    message.text,
                    System.currentTimeMillis(),
                    conversation.label,
                    message.path,
                    message.type != 1
                )
            )
        }

        val conversationStyle = NotificationCompat.MessagingStyle(senderPerson)
        ndb.findBySender(conversation.sender).forEach {
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
            if (it.path != null && getMimeType(it.path!!).startsWith("photo")) {
                msg.setData(getMimeType(it.path!!), Uri.fromFile(File(it.path!!)))
            }
            conversationStyle.addMessage(msg)
        }

        val remoteInput: RemoteInput = RemoteInput.Builder(KEY_TEXT_REPLY).run {
            setLabel(mContext.getString(R.string.reply))
            build()
        }
        val replyPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            mContext.applicationContext,
            conversation.id!!.toInt(),
            Intent(mContext, NotificationActionReceiver::class.java)
                .setAction(ACTION_REPLY)
                .putExtra("conversation", conversation),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action: NotificationCompat.Action = NotificationCompat.Action.Builder(
            R.drawable.ic_reply, mContext.getString(R.string.reply), replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = Builder(mContext, conversation.label.toString())
            .setCategory(CATEGORY_MESSAGE)
            .setGroup(GROUP_DEFAULT)
            .setSmallIcon(R.drawable.message)
            .setContentIntent(contentPI)
            .setDeleteIntent(cancelPI)
            .setAutoCancel(true)
            .setStyle(conversationStyle).apply {
                if (message.type != 1) setNotificationSilent()
                if (conversation.sender.first().isDigit()) addAction(action)
        }

        notificationManager.notify(conversation.id!!.toInt(), notification.build())
        showSummaryNotification()
    }
}