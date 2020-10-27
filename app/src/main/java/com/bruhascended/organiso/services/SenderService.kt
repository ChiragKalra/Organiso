package com.bruhascended.organiso.services

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.widget.Toast
import com.bruhascended.core.db.Message
import com.bruhascended.organiso.BuildConfig.APPLICATION_ID
import com.bruhascended.organiso.R
import com.bruhascended.core.constants.*
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.db.MessageDao
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationDao
import com.bruhascended.organiso.ConversationActivity.Companion.activeConversationNumber
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import java.io.ByteArrayOutputStream
import java.io.InputStream
import com.klinker.android.send_message.Message as SuperMessage

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

class SenderService: Service() {

    private val smsSentAction = "$APPLICATION_ID.MMS_SENT"
    private val mmsSentAction = "$APPLICATION_ID.SMS_SENT"
    private val deliveredAction = "$APPLICATION_ID.SMS_DELIVERED"
    private val mContext = this

    private lateinit var mContactProvider: ContactsProvider

    private val settings = Settings().apply {
        useSystemSending = true
        deliveryReports = true
    }

    private fun getDao(number: String): MessageDao {
        return if (activeConversationNumber == number) {
            activeConversationDao!!
        } else {
            MessageDbFactory(mContext).of(number).manager()
        }
    }

    private fun registerDeliveredReceiver(number: String, id: Int) {
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent) {
                val gotId = Uri.parse(arg1.getStringExtra(EXTRA_MESSAGE_URI)).lastPathSegment?.toInt() ?: return
                if (gotId == id && resultCode == Activity.RESULT_OK) {
                    getDao(number).markDelivered(id)
                    mContext.unregisterReceiver(this)
                }
            }
        }, IntentFilter(deliveredAction))
    }

    private fun updateSentStatus(number: String, oldId : Int, id: Int, status: Int) {
        if (status == MESSAGE_TYPE_SENT) {
            registerDeliveredReceiver(number, id)
        }
        getDao(number).apply {
            val old = getById(oldId)
            if (old != null) {
                deleteFromInternal(old)
                old.type = status
                old.id = id
                insert(old)
            } else {
                updateStatus(id, status)
            }

        }
    }

    private fun registerSentReceiver(number: String, oldId: Int) {
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context, arg1: Intent) {
                val id = Uri.parse(arg1.getStringExtra(EXTRA_MESSAGE_URI)).lastPathSegment?.toInt() ?: return
                updateSentStatus(number, oldId, id,
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            mContext.unregisterReceiver(this)
                            MESSAGE_TYPE_SENT
                        }
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.service_provider_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            MESSAGE_TYPE_FAILED
                        }
                        SmsManager.RESULT_ERROR_NO_SERVICE -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.no_service),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            MESSAGE_TYPE_FAILED
                        }
                        else -> {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            MESSAGE_TYPE_FAILED
                        }
                    }
                )
            }
        }, IntentFilter().apply {
            addAction(smsSentAction)
            addAction(mmsSentAction)
        })
    }

    private fun updateConversation(number: String, time: Long) {
        val daos = MainDaoProvider(mContext).getMainDaos()
        for (i in 0..4) {
            val res = daos[i].findByNumber(number)
            if (res != null) {
                daos[i].updateTime(number, time)
                return
            }
        }
        daos[LABEL_PERSONAL].insert(
            Conversation(
                number,
                time = time,
                label = LABEL_PERSONAL,
                forceLabel = LABEL_PERSONAL,
            )
        )
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

    private fun sendMessage(number: String, smsText: String, data: Uri?) {
        val date = System.currentTimeMillis()
        val transaction = Transaction(mContext, settings).apply {
            setExplicitBroadcastForSentMms(Intent(mmsSentAction))
        }

        getDao(number).apply {
            val id = insert(
                Message(
                    smsText,
                    MESSAGE_TYPE_QUEUED,
                    date,
                    path = mContext.saveFile(data, date.toString())
                )
            ).toInt()
            registerSentReceiver(number, id)
        }
        updateConversation(number, date)
        val message = SuperMessage(smsText, number).apply {
            if (data != null) {
                val iStream: InputStream = mContext.contentResolver.openInputStream(data)!!
                val type = mContext.contentResolver.getType(data) ?: getMimeType(data.path!!)
                addMedia(getBytes(iStream), type)
            }
        }
        transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
    }

    private fun retry(number: String, messageId: Int) {
        val date = System.currentTimeMillis()
        val transaction = Transaction(mContext, settings).apply {
            setExplicitBroadcastForSentMms(Intent(mmsSentAction))
        }

        getDao(number).apply {
            val oldMessage = getDao(number).getById(messageId)!!
            delete(mContext, oldMessage, deleteFile = false)
            val id = insert(
                oldMessage.apply {
                    time = date
                    type = MESSAGE_TYPE_QUEUED
                    id = null
                }
            )
            registerSentReceiver(number, id.toInt())

            updateConversation(number, date)
            val message = SuperMessage(oldMessage.text, number).apply {
                if (oldMessage.path != null) {
                    val iStream: InputStream = mContext.contentResolver.openInputStream(Uri.parse(oldMessage.path))!!
                    val type = mContext.contentResolver.getType(Uri.parse(oldMessage.path)) ?: getMimeType(oldMessage.path!!)
                    addMedia(getBytes(iStream), type)
                }
            }
            transaction.sendNewMessage(message, Transaction.NO_THREAD_ID)
        }
    }

    override fun onCreate() {
        mContactProvider = ContactsProvider(mContext)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Thread {
            val retry = intent.getIntExtra(EXTRA_RETRY_ID, -1)
            if (retry == -1) {
                sendMessage(
                    intent.getStringExtra(EXTRA_NUMBER)!!,
                    intent.getStringExtra(EXTRA_MESSAGE_TEXT)!!,
                    intent.data
                )
            } else {
                retry(
                    intent.getStringExtra(EXTRA_NUMBER)!!,
                    retry
                )
            }
        }.start()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

}
