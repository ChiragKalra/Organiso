package com.bruhascended.sms.services

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.bruhascended.sms.BuildConfig.APPLICATION_ID
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.activeConversationDao
import com.bruhascended.sms.mainViewModel
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


/*
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later
*/

class MMSSender(
    private val mContext: Context,
    private var conversation: Conversation
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

    private fun addSmsToDb(date: Long, type: Int) {
        val qs = activeConversationDao.search(date).first()
        qs.type = type
        activeConversationDao.update(qs)
    }

    private fun addMmsToDb(date: Long) {
        Thread {
            val message = Message(
                null,
                conversation.sender,
                smsText,
                6,
                date,
                0,
                path = saveMedia(date)
            )
            val qs = activeConversationDao.search(date)
            for (m in qs) activeConversationDao.delete(m)
            activeConversationDao.insert(message)

            if (conversation.id == null) {
                var found = false
                for (i in 0..4) {
                    val res = mainViewModel.daos[i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        found = true
                        conversation = res[0]
                        break
                    }
                }
                conversation.apply {
                    time = date
                    lastSMS = smsText
                    lastMMS = true
                    read = false
                }
                if (found) mainViewModel.daos[conversation.label].update(conversation)
                else mainViewModel.daos[conversation.label].insert(conversation)
            } else {
                conversation.apply {
                    time = date
                    lastSMS = smsText
                    lastMMS = true
                    read = false
                }
                mainViewModel.daos[conversation.label].update(conversation)
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

    fun sendMMS(smsText: String, data: Uri, type: String) {
        val date = System.currentTimeMillis()
        this.smsText = smsText
        uri = data
        typeString = type

        addMmsToDb(date)
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()


        val transaction = Transaction(mContext, settings).apply {
            setExplicitBroadcastForSentMms(Intent(sentAction))
        }

        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> addSmsToDb(date, 2)
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        Toast.makeText(
                            mContext,
                            "Service provider error",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(date, 5)
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        Toast.makeText(
                            mContext,
                            "No service",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(date, 5)
                    }
                    else -> {
                        Toast.makeText(
                            mContext,
                            "Error",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(date, 5)
                    }
                }
                mContext.unregisterReceiver(this)
            }
        }, IntentFilter(sentAction))

        val message = MMS(smsText, conversation.sender)
        val iStream: InputStream = mContext.contentResolver.openInputStream(uri)!!
        message.addMedia(getBytes(iStream), type)

        transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
    }

}