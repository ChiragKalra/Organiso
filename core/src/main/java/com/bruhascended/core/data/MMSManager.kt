package com.bruhascended.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.webkit.MimeTypeMap
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
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
class MMSManager (
    private val mContext: Context,
    private var senderNameMap: HashMap<String, String>? = null
) {
    private val cm = ContactsManager(mContext)
    private val mMainDaoProvider = MainDaoProvider(mContext)

    private val tMgr = mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var mPhoneNumber = try {
        cm.getClean(tMgr.line1Number)
    } catch (e: Exception) {
        ""
    }

    private fun getAddressNumber(id: Int): Pair<Boolean, String> {
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
                        putMMS(id.toInt(), date)
                    }
                } while (moveToNext())
            }
            close()
        }
    }

    fun putMMS(
        mmsId: Int,
        date: Long = System.currentTimeMillis(),
        activeNumber: String? = null,
        activeDao: MessageDao? = null,
    ): Pair<Message, Conversation>? {
        if (senderNameMap == null) {
            senderNameMap = cm.getContactsHashMap()
        }

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
            date, path = file, id = mmsId
        )

        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mMainDaoProvider.getMainDaos()[i].findByNumber(rawNumber)
            if (got != null) {
                conversation = got
                mMainDaoProvider.getMainDaos()[i].delete(got)
            }
        }

        conversation = if (conversation != null) {
            conversation.apply {
                if (time < message.time) {
                    read = activeNumber == rawNumber
                    time = message.time
                }
                label = LABEL_PERSONAL
                forceLabel = LABEL_PERSONAL
                mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(this)
            }
            conversation
        } else {
            val con = Conversation(
                sender.second,
                read = activeNumber == rawNumber,
                time = message.time,
                label = LABEL_PERSONAL,
                forceLabel = LABEL_PERSONAL,
            )
            mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(con)
            con
        }

        val dao = if (activeNumber == rawNumber) {
            activeDao!!
        } else {
            MessageDbFactory(mContext).of(rawNumber).manager()
        }

        dao.insert(message)
        return message to conversation
    }
}