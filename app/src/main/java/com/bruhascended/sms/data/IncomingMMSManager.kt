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

package com.bruhascended.sms.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.conversationDao
import com.bruhascended.sms.ui.conversationSender
import com.bruhascended.sms.ui.isMainViewModelNull
import com.bruhascended.sms.ui.main.MainViewModel
import com.bruhascended.sms.ui.mainViewModel
import java.io.*


class IncomingMMSManager(private val context: Context) {
    private var date: Long = 0

    @SuppressLint("Recycle")
    private fun getAddressNumber(id: String): String? {
        val selection = "type=137 AND msg_id=$id"
        val uriAddress = Uri.parse("content://mms/${id}/addr")
        val cursor = context.contentResolver.query(
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
        return address
    }

    private fun getMmsText(id: String): String {
        val partURI = Uri.parse("content://mms/part/$id")
        val inp: InputStream? = context.contentResolver.openInputStream(partURI)
        val sb = StringBuilder()
        if (inp != null) {
            val isr = InputStreamReader(inp, "UTF-8")
            val reader = BufferedReader(isr)
            var temp: String? = reader.readLine()
            while (temp != null) {
                sb.append(temp)
                temp = reader.readLine()
            }
        }
        return sb.toString()
    }

    private fun saveFile(_id: String, typeString: String): String {
        val partURI = Uri.parse("content://mms/part/$_id")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(typeString)
        val name = "$date.$ext"
        val destination = File(context.filesDir, name)
        val output = FileOutputStream(destination)
        val input = context.contentResolver.openInputStream(partURI) ?: return ""
        val buffer = ByteArray(4 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
        return destination.absolutePath
    }

    @SuppressLint("Recycle")
    fun putMMS(uri: Uri) : Pair<Message, Conversation> {
        date = System.currentTimeMillis()
        val mmsId = uri.toString().split("/").last()

        val selectionPart = "mid=$mmsId"
        val partUri = Uri.parse("content://mms/part")
        val cursor = context.contentResolver.query(
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
                } else if (
                    typeString.startsWith("video") ||
                    typeString.startsWith("image") ||
                    typeString.startsWith("audio")
                ) {
                    file = saveFile(partId, typeString)
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        val sender = getAddressNumber(mmsId)!!

        val rawNumber = ContactsManager(context).getRaw(sender)
        val senderNameMap = ContactsManager(context).getContactsHashMap()

        val message = Message(
            null,
            sender,
            body,
            1,
            date,
            0,
            false,
            file
        )

        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    context, ConversationDatabase::class.java,
                    context.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mainViewModel.daos[i].findBySender(rawNumber)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got.slice(1 until got.size))
                    mainViewModel.daos[i].delete(item)
                break
            }
        }

        conversation = if (conversation != null) {
            conversation.apply {
                read = false
                time = message.time
                lastSMS = message.text
                lastMMS = true
                label = 0
                forceLabel = 0
                name = senderNameMap[rawNumber]
                mainViewModel.daos[0].update(this)
            }
            conversation
        } else {
            val con = Conversation(
                null,
                rawNumber,
                senderNameMap[rawNumber],
                "",
                false,
                message.time,
                message.text,
                0,
                0,
                FloatArray(5) {
                    if (it == 0) 1f else 0f
                },
                lastMMS = true
            )
            mainViewModel.daos[0].insert(con)
            con.id = mainViewModel.daos[0].findBySender(rawNumber).first().id
            con
        }

        val mdb = if (conversationSender == rawNumber) conversationDao
        else Room.databaseBuilder(
            context, MessageDatabase::class.java, rawNumber
        ).build().manager()

        mdb.insert(message)

        return mdb.search(message.time).first() to conversation
    }
}