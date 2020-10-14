package com.bruhascended.organiso.services

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.room.Room
import com.bruhascended.organiso.BuildConfig.APPLICATION_ID
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.activeConversationDao
import com.bruhascended.organiso.activeConversationSender
import com.bruhascended.organiso.db.MessageDatabase
import com.bruhascended.organiso.mainViewModel
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import java.io.*
import com.klinker.android.send_message.Message as MMS

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

class MMSSender(
    private val mContext: Context,
    private var conversations: Array<Conversation>
) {
    private lateinit var typeString: String
    private lateinit var uri: Uri
    private lateinit var smsText: String
    private val settings = Settings().apply { useSystemSending = true}

    private val sentAction = "${APPLICATION_ID}.MMS_SENT"

    private fun saveMedia(date: Long): String {
        val name = date.toString() + "." +
                MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString)
        val destination = File(mContext.filesDir, name)
        val output: OutputStream = FileOutputStream(destination)
        val input = mContext.contentResolver.openInputStream(uri)!!
        val buffer = ByteArray(4 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
        return destination.absolutePath
    }

    private fun updateDbMms(conversation: Conversation, date: Long, type: Int) {
        val conversationDao = if (activeConversationSender == null) Room.databaseBuilder(
            mContext, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager() else activeConversationDao

        val qs = conversationDao.search(date).first()
        qs.type = type
        conversationDao.update(qs)
    }

    private fun addMmsToDb(conversation: Conversation, date: Long, retryIndex: Long?) {
        Thread {
            val message = Message(
                retryIndex,
                conversation.sender,
                smsText,
                6,
                date,
                0,
                path = saveMedia(date)
            )

            val conversationDao = if (activeConversationSender == null) Room.databaseBuilder(
                mContext, MessageDatabase::class.java, conversation.sender
            ).allowMainThreadQueries().build().manager() else activeConversationDao
            val qs = conversationDao.search(date)
            for (m in qs) {
                message.id = m.id
                conversationDao.delete(m)
            }
            if (retryIndex != null) conversationDao.delete(message)
            conversationDao.insert(message)

            var newCon = conversation
            if (conversation.id == null) {
                for (i in 0..4) {
                    val res = mainViewModel.daos[i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        conversations[
                                conversations.indexOf(conversations.first {
                                    it.sender == conversation.sender
                                })
                        ] = res[0]
                        newCon = res[0]
                        break
                    }
                }
            }

            newCon.apply {
                time = date
                lastSMS = smsText
                lastMMS = true
                read = true
                if (newCon.id != null) mainViewModel.daos[label].update(this)
                else mainViewModel.daos[label].insert(this)
            }
        }.start()
    }

    private fun getBytes(inputStream: InputStream): ByteArray {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len: Int
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        return byteBuffer.toByteArray()
    }

    fun sendMMS(smsText: String, data: Uri, type: String, retryIndex: Long? = null) {
        val date = System.currentTimeMillis()
        this.smsText = smsText
        uri = data
        typeString = type

        conversations.forEach {
            addMmsToDb(it, date, retryIndex)
            mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
                .putLong("last", System.currentTimeMillis()).apply()

            val transaction = Transaction(mContext, settings).apply {
                setExplicitBroadcastForSentMms(Intent(sentAction))
            }

            mContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(arg0: Context?, arg1: Intent?) {
                    when (resultCode) {
                        Activity.RESULT_OK -> updateDbMms(it, date, 2)
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Toast.makeText(
                                mContext,
                                "Service provider error",
                                Toast.LENGTH_SHORT
                            ).show()
                            updateDbMms(it, date, 5)
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Toast.makeText(
                                mContext,
                                "No service",
                                Toast.LENGTH_SHORT
                            ).show()
                            updateDbMms(it, date, 5)
                        }
                        else -> {
                            Toast.makeText(
                                mContext,
                                "Error",
                                Toast.LENGTH_SHORT
                            ).show()
                            updateDbMms(it, date, 5)
                        }
                    }
                    mContext.unregisterReceiver(this)
                }
            }, IntentFilter(sentAction))

            val message = MMS(smsText, it.sender)
            val iStream: InputStream = mContext.contentResolver.openInputStream(uri)!!
            message.addMedia(getBytes(iStream), type)

            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
        }
    }

}