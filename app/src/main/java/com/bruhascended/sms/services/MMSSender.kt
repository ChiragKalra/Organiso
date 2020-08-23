package com.bruhascended.sms.services

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.webkit.MimeTypeMap
import android.widget.ImageButton
import android.widget.Toast
import com.bruhascended.sms.conversationDao
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.mainViewModel
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


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
    private var conversation: Conversation,
    private val sendButton: ImageButton
) {
    private lateinit var typeString: String
    private lateinit var uri: Uri
    private lateinit var smsText: String

    @SuppressLint("SetWorldReadable", "SetWorldWritable")
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

    private fun addSmsToGlobal(message: Message) {
        val romsThatDontSaveSms = arrayOf("HUAWEI")
        if (android.os.Build.MANUFACTURER in romsThatDontSaveSms) {
            try {
                val values = ContentValues()
                values.put("address", message.sender)
                values.put("body", message.text)
                values.put("read", 0)
                values.put("date", message.time)
                values.put("type", message.type)
                mContext.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                mContext.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                mContext.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun addSmsToDb(date: Long, type: Int) {
        Thread {
            val message = Message(
                null,
                conversation.sender,
                smsText,
                type,
                date,
                0,
                path = saveMedia(date)
            )
            val qs = conversationDao.search(date)
            for (m in qs) conversationDao.delete(m)
            conversationDao.insert(message)
            if (type == 2) addSmsToGlobal(message)

            if (conversation.id == null) {
                var found = false
                for (i in 0..4) {
                    val res = mainViewModel!!.daos!![i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        found = true
                        conversation = res[0]
                        break
                    }
                }
                conversation.time = date
                conversation.lastSMS = smsText
                conversation.lastMMS = true
                if (found) mainViewModel!!.daos!![conversation.label].update(conversation)
                else mainViewModel!!.daos!![conversation.label].insert(conversation)
            } else {
                conversation.time = date
                conversation.lastSMS = smsText
                conversation.lastMMS = true
                mainViewModel!!.daos!![conversation.label].update(conversation)
            }
        }.start()
    }

    fun sendSMS(smsText: String, data: Uri, type: String) {
        val date = System.currentTimeMillis()
        this.smsText = smsText
        uri = data
        typeString = type

        sendButton.isEnabled = false

        val smsManager = SmsManager.getDefault()
        val sentPI = PendingIntent.getBroadcast(mContext, 0, Intent("SENT"), 0)

        addSmsToDb(date, 6)
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
        }, IntentFilter("SENT"))

        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()

        smsManager.sendMultimediaMessage(mContext, data, null, null, sentPI)

        sendButton.isEnabled = true
    }

}