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

package com.bruhascended.sms.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.*
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ml.getOtp


class MessageNotificationManager(private val mContext: Context) {
    private val copyAction = "OTP_COPIED"
    private val deleteAction = "MESSAGE_DELETED"

    private val prefs = PreferenceManager.getDefaultSharedPreferences(mContext)

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

    fun sendSmsNotification(pair: Pair<Message, Conversation>) {
        val conversation: Conversation = pair.second
        val message: Message = pair.first

        val id = System.currentTimeMillis() % Int.MAX_VALUE

        if (conversation.isMuted || conversation.sender == activeConversationSender || conversation.label == 5) return
        val yeah = Intent(mContext, ConversationActivity::class.java)
            .putExtra("ye", conversation)
        yeah.flags = Intent.FLAG_ACTIVITY_TASK_ON_HOME
        val pendingIntent = PendingIntent.getActivity(
            mContext, 0, yeah, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val otp = getOtp(message.text)

        val copyPI = PendingIntent.getBroadcast(mContext, 0, Intent(copyAction), 0)
        val deletePI = PendingIntent.getBroadcast(mContext, 0, Intent(deleteAction), 0)

        mContext.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                copyToClipboard(otp!!)
            }
        }, IntentFilter(copyAction))
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
                NotificationManagerCompat.from(mContext).cancel(id.toInt())
                Toast.makeText(context, "Deleted", Toast.LENGTH_LONG).show()
                mContext.applicationContext.unregisterReceiver(this)
            }
        }, IntentFilter(deleteAction))

        val builder = when {
            message.path != null -> {
                NotificationCompat.Builder(mContext, conversation.label.toString())
                    .setContentText(message.text)
                    .setContentTitle("Media from ${conversation.name ?: message.sender}")
                    .apply {
                        if (getMimeType(message.path!!).startsWith("image")) {
                            val bitmap = BitmapFactory.decodeFile(message.path!!)
                            setLargeIcon(bitmap)
                            setStyle(
                                NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                                    .bigLargeIcon(null)
                            )
                        }
                    }
            }
            otp == null -> {
                NotificationCompat.Builder(mContext, conversation.label.toString())
                    .setContentText(message.text)
                    .setContentTitle(conversation.name ?: message.sender)
            }
            else -> {
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
                    )
                    .addAction(
                        R.drawable.ic_delete,
                        mContext.getString(R.string.delete),
                        deletePI
                    )
            }
        }.setSmallIcon(R.drawable.message)
            .setGroup(conversation.sender)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(mContext).notify(id.toInt(), builder)
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