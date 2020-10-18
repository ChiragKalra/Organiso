package com.bruhascended.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.webkit.MimeTypeMap
import com.bruhascended.core.data.SMSManager.Companion.ACTION_NEW_MESSAGE
import com.bruhascended.core.data.SMSManager.Companion.EXTRA_MESSAGE
import com.bruhascended.core.data.SMSManager.Companion.LABEL_PERSONAL
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_INBOX
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_SENT
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.db.MainDaoProvider
import java.io.*
import java.lang.Exception

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

@SuppressLint("Recycle", "MissingPermission", "HardwareIds")
class MMSManager(private val mContext: Context) {
    private val cm = ContactsManager(mContext)
    private val senderNameMap = cm.getContactsHashMap()
    private val mMainDaoProvider = MainDaoProvider(mContext)

    private val tMgr = mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var mPhoneNumber = cm.getClean(tMgr.line1Number)

    private fun getAddressNumber(id: String): Pair<Boolean, String> {
        var selection = "type=137 AND msg_id=$id"
        val uriAddress = Uri.parse("content://mms/${id}/addr")
        var cursor = mContext.contentResolver.query(
            uriAddress, arrayOf("address"), selection, null, null
        )!!
        var address = ""
        if (cursor.moveToFirst()) {
            do {
                address = cursor.getString(cursor.getColumnIndex("address"))
                if (address != null) break
            } while (cursor.moveToNext())
        }
        cursor.close()
        if (address != mPhoneNumber) return false to address

        selection = "type=151 AND msg_id=$id"
        cursor = mContext.contentResolver.query(
            uriAddress, arrayOf("address"), selection, null, null
        )!!
        if (cursor.moveToFirst()) {
            do {
                address = cursor.getString(cursor.getColumnIndex("address"))
                if (address != null) break
            } while (cursor.moveToNext())
        }
        cursor.close()
        return true to address
    }

    private fun getMmsText(id: String): String {
        val partURI = Uri.parse("content://mms/part/$id")
        val sb = StringBuilder()
        try {
            val inp: InputStream? = mContext.contentResolver.openInputStream(partURI)
            if (inp != null) {
                val isr = InputStreamReader(inp, "UTF-8")
                val reader = BufferedReader(isr)
                var temp: String? = reader.readLine()
                while (temp != null) {
                    sb.append(temp)
                    temp = reader.readLine()
                }
            }
        } catch (e: Exception) { }
        return sb.toString()
    }

    private fun saveFile(_id: String, typeString: String, date: Long): String {
        val partURI = Uri.parse("content://mms/part/$_id")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString)
        val name = "$date.$ext"
        val destination = File(mContext.filesDir, name)
        val output = FileOutputStream(destination)
        val input = mContext.contentResolver.openInputStream(partURI) ?: return ""
        val buffer = ByteArray(4 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
        return destination.absolutePath
    }

    fun getAllMMS(lastDate: String) {
        mContext.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            null,
            "date" + ">?",
            arrayOf(lastDate),
            "date ASC"
        ) ?.apply {
            if (moveToFirst()) {
                val idColumn = getColumnIndex("_id")
                val dateColumn = getColumnIndex("date")
                val textColumn = getColumnIndex("text_only")
                do {
                    val id = getString(idColumn)
                    val isMms = getString(textColumn) == "0"
                    val date = getString(dateColumn).toLong()
                    if (isMms) {
                        putMMS(id, date)
                    }
                } while (moveToNext())
            }
            close()
        }
    }

    fun putMMS(
        mmsId: String, date: Long = System.currentTimeMillis(),
        init: Boolean = true, activeSender: String? = null
    ): Pair<Message, Conversation>? {
        val selectionPart = "mid=$mmsId"
        val partUri = Uri.parse("content://mms/part")
        val cursor = mContext.contentResolver.query(
            partUri, null,
            selectionPart, null, null
        )!!
        var body = ""
        var file: String? = null
        if (cursor.moveToFirst()) {
            do {
                val partId: String = cursor.getString(cursor.getColumnIndex("_id"))
                val typeString = cursor.getString(cursor.getColumnIndex("ct"))
                if ("text/plain" == typeString) {
                    body = getMmsText(partId)
                } else if (file==null &&
                    (typeString.startsWith("video") ||
                    typeString.startsWith("image") ||
                    typeString.startsWith("audio"))
                ) {
                    file = saveFile(partId, typeString, date)
                }
                if (file!=null && body.isNotEmpty()) break
            } while (cursor.moveToNext())
        }
        cursor.close()

        if (file == null) return null

        val sender = getAddressNumber(mmsId)
        val sentByUser = sender.first
        val rawNumber = cm.getClean(sender.second)

        val message = Message(
            body, if (sentByUser) MESSAGE_TYPE_SENT else MESSAGE_TYPE_INBOX,
            date, path = file
        )

        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mMainDaoProvider.getMainDaos()[i].findBySender(rawNumber)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got)
                    mMainDaoProvider.getMainDaos()[i].delete(item)
            }
        }

        conversation = if (conversation != null) {
            conversation.apply {
                if (time < message.time) {
                    read = init
                    time = message.time
                    lastSMS = message.text
                    lastMMS = true
                }
                label = LABEL_PERSONAL
                forceLabel = LABEL_PERSONAL
                name = senderNameMap[rawNumber]
                mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(this)
            }
            conversation
        } else {
            val con = Conversation(
                sender.second,
                rawNumber,
                senderNameMap[rawNumber],
                read = init,
                time = message.time,
                lastSMS = message.text,
                label = LABEL_PERSONAL,
                forceLabel = LABEL_PERSONAL,
                lastMMS = true
            )
            mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(con)
            con.id = mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].findBySender(rawNumber).first().id
            con
        }

        return if (activeSender == rawNumber) {
            mContext.sendBroadcast (
                Intent(ACTION_NEW_MESSAGE).apply {
                    setPackage(mContext.applicationInfo.packageName)
                    putExtra(EXTRA_MESSAGE, message)
                }
            )
            null
        } else {
            val mdb = MessageDbFactory(mContext).of(rawNumber)
            mdb.manager().insert(message)
            val a = mdb.manager().search(message.time).first() to conversation
            mdb.close()
            a
        }
    }
}