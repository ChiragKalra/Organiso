package com.bruhascended.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Threads.getOrCreateThreadId
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

    private fun getAddressNumber(id: Int): Pair<Boolean, String> {
        var threadId = -1L
        try {
            mContext.contentResolver.query(
                Uri.parse("content://mms/${id}"),
                arrayOf("thread_id"),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    threadId = it.getLong(0)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MMSManager", "Error getting threadId", e)
        }

        var address = ""
        val uriAddress = Uri.parse("content://mms/${id}/addr")
        try {
            mContext.contentResolver.query(
                uriAddress, arrayOf("address"), "type=137 AND msg_id=$id", null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val addrIdx = cursor.getColumnIndex("address")
                    if (addrIdx != -1) {
                        address = cursor.getString(addrIdx) ?: ""
                    }
                }
            }
            if (address.isNotEmpty()) {
                if (getOrCreateThreadId(mContext, address) == threadId) {
                    return false to address
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MMSManager", "Error getting type 137 address", e)
        }

        try {
            mContext.contentResolver.query(
                uriAddress, null, "type=151 AND msg_id=$id", null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val addrIdx = cursor.getColumnIndex("address")
                    if (addrIdx != -1) {
                        address = cursor.getString(addrIdx) ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MMSManager", "Error getting type 151 address", e)
        }
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
        ) ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndex("_id")
                val dateColumn = cursor.getColumnIndex("date")
                val textColumn = cursor.getColumnIndex("text_only")
                val typeColumn = cursor.getColumnIndex("msg_box")
                
                if (idColumn != -1 && dateColumn != -1 && textColumn != -1 && typeColumn != -1) {
                    do {
                        val id = cursor.getString(idColumn)
                        val isMms = cursor.getString(textColumn) == "0"
                        val date = cursor.getString(dateColumn).toLong() * 1000
                        val type = cursor.getString(typeColumn).toInt()
                        if (isMms) {
                            putMMS(id.toInt(), type, init = true, date = date)
                        }
                    } while (cursor.moveToNext())
                }
            }
        }
    }

    fun putMMS(
        mmsId: Int,
        type: Int,
        init: Boolean = false,
        date: Long = System.currentTimeMillis(),
        activeNumber: String? = null,
        activeDao: MessageDao? = null,
    ): Pair<Message, Conversation>? {
        if (senderNameMap == null) {
            senderNameMap = cm.getContactsHashMap()
        }

        val selectionPart = "mid=$mmsId"
        val partUri = Uri.parse("content://mms/part")
        var body = ""
        var file: String? = null
        try {
            mContext.contentResolver.query(
                partUri, null,
                selectionPart, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex("_id")
                    val ctIdx = cursor.getColumnIndex("ct")
                    if (idIdx != -1 && ctIdx != -1) {
                        do {
                            val partId: String = cursor.getString(idIdx)
                            val typeString = cursor.getString(ctIdx)
                            if ("text/plain" == typeString) {
                                body = getMmsText(partId)
                            } else if (file == null &&
                                (typeString.startsWith("video") ||
                                        typeString.startsWith("image") ||
                                        typeString.startsWith("audio"))
                            ) {
                                file = saveFile(partId, typeString, date)
                            }
                            if (file != null && body.isNotEmpty()) break
                        } while (cursor.moveToNext())
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MMSManager", "Error querying MMS parts", e)
        }


        if (file==null && body.isBlank()) return null

        val sender = getAddressNumber(mmsId)
        val mType = if (type in 0..2) {
            if (sender.first) MESSAGE_TYPE_SENT else MESSAGE_TYPE_INBOX
        } else {
            type
        }
        val rawNumber = cm.getClean(sender.second)

        val message = Message(
            body, mType, date, path = file, id = mmsId
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
                    read = init
                    time = message.time
                }
                label = LABEL_PERSONAL
                forceLabel = LABEL_PERSONAL
                mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(this)
            }
            conversation
        } else {
            val con = Conversation(
                rawNumber,
                read = init,
                time = message.time,
                label = LABEL_PERSONAL,
                forceLabel = LABEL_PERSONAL,
            )
            mMainDaoProvider.getMainDaos()[LABEL_PERSONAL].insert(con)
            con
        }

        if (activeNumber == rawNumber) {
            activeDao!!.insert(message)
            return null
        } else {
            MessageDbFactory(mContext).of(rawNumber).apply {
                manager().insert(message)
                close()
            }
        }
        return message to conversation
    }
}