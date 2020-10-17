package com.bruhascended.core.data

import android.content.Context
import android.net.Uri
import com.bruhascended.core.BuildConfig.LIBRARY_PACKAGE_NAME
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.core.analytics.AnalyticsLogger.Companion.EVENT_CONVERSATION_ORGANISED
import com.bruhascended.core.analytics.AnalyticsLogger.Companion.PARAM_INIT
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.ml.OrganizerModel
import com.bruhascended.core.db.MainDaoProvider

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

class SMSManager(private val mContext: Context) {

    companion object {
        const val EXTRA_MESSAGE = "MESSAGE"
        const val EXTRA_MESSAGE_DATE = "MESSAGE_DATE"
        const val EXTRA_MESSAGE_TYPE = "MESSAGE_TYPE"
        const val ACTION_NEW_MESSAGE = "$LIBRARY_PACKAGE_NAME.NEW_MESSAGE"
        const val ACTION_OVERWRITE_MESSAGE = "$LIBRARY_PACKAGE_NAME.OVERWRITE_MESSAGE"
        const val ACTION_UPDATE_STATUS_MESSAGE = "$LIBRARY_PACKAGE_NAME.UPDATE_MESSAGE"

        const val MESSAGE_TYPE_ALL = 0
        const val MESSAGE_TYPE_INBOX = 1
        const val MESSAGE_TYPE_SENT = 2
        const val MESSAGE_TYPE_DRAFT = 3
        const val MESSAGE_TYPE_OUTBOX = 4
        const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
        const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later

        internal val ARR_LABEL_STR = arrayOf (
            "Personal",
            "Important",
            "Transactions",
            "Promotions",
            "Spam",
            "Blocked"
        )

        const val LABEL_PERSONAL = 0
        const val LABEL_IMPORTANT = 1
        const val LABEL_TRANSACTIONS = 2
        const val LABEL_PROMOTIONS = 3
        const val LABEL_SPAM = 4
        const val LABEL_BLOCKED = 5
    }

    private val nn = OrganizerModel(mContext)
    private val cm = ContactsManager(mContext)
    private val mmsManager = MMSManager(mContext)
    private val sp = mContext.getSharedPreferences("local", Context.MODE_PRIVATE)

    private val messages = HashMap<String, ArrayList<Message>>()
    private val senderToProbs = HashMap<String, FloatArray>()

    private var done = 0
    private var index = 0
    private var total = 0

    private var timeTaken = 0L
    private var startTime = 0L

    private lateinit var senderNameMap: HashMap<String, String>

    private lateinit var mmsThread: Thread

    var onStatusChangeListener: (Int) -> Unit = {}
    var onProgressListener: (Float) -> Unit = {}
    var onEtaChangeListener: (Long) -> Unit = {}

    private fun finish() {
        onStatusChangeListener(2)

        nn.close()
        senderNameMap.clear()
        senderToProbs.clear()

        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis())
            .putInt("index", 0)
            .putInt("done", 0)
            .apply()
    }

    private fun updateProgress(size: Int) {
        done += size
        timeTaken = (System.currentTimeMillis()-startTime)
        val per = done * 100f / total
        val eta = (System.currentTimeMillis()-startTime) * (100/per-1)
        onProgressListener(per)
        onEtaChangeListener(eta.toLong())

        AnalyticsLogger(mContext).log(EVENT_CONVERSATION_ORGANISED, PARAM_INIT)
    }

    private fun saveMessage(ind: Int, sender: String, messages: ArrayList<Message>, label: Int) {
        if (sender.isBlank()) return

        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = MainDaoProvider(mContext).getMainDaos()[i].findBySender(sender)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got.slice(1 until got.size))
                    MainDaoProvider(mContext).getMainDaos()[i].delete(item)
                break
            }
        }

        if (conversation != null) {
            conversation.apply {
                read = false
                time = messages.last().time
                lastSMS =  messages.last().text
                lastMMS = false
                name = senderNameMap[sender]
                MainDaoProvider(mContext).getMainDaos()[LABEL_PERSONAL].update(this)
            }
        } else {
            val con = Conversation(
                sender,
                senderNameMap[sender],
                time = messages.last().time,
                lastSMS = messages.last().text,
                label = label,
                forceLabel = if (label == 0) 0 else -1,
                probabilities = senderToProbs[sender] ?:
                FloatArray(5) { if (it == 0) 1f else 0f }
            )
            MainDaoProvider(mContext).getMainDaos()[label].insert(con)
        }

        MessageDbFactory(mContext).of(sender).apply {
            manager().insertAll(messages.toList())
            close()
        }

        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putInt("index", ind + 1)
            .putInt("done", done)
            .putLong("timeTaken", timeTaken)
            .apply()
    }

    fun getMessages() {
        val lastDate = sp.getLong("last", 0).toString()

        mContext.contentResolver.query(
            Uri.parse("content://sms/"),
            null,
            "date" + ">?",
            arrayOf(lastDate),
            "date ASC"
        ) ?.apply {
            if (moveToFirst()) {
                val nameID = getColumnIndex("address")
                val messageID = getColumnIndex("body")
                val dateID = getColumnIndex("date")
                val typeID = getColumnIndex("type")

                do {
                    var name = getString(nameID)
                    val messageContent = getString(messageID)
                    if (name != null && !messageContent.isNullOrEmpty()) {
                        name = cm.getRaw(name)
                        val message = Message(
                            messageContent, getString(typeID).toInt(), getString(dateID).toLong()
                        )
                        if (messages.containsKey(name)) messages[name]?.add(message)
                        else messages[name] = arrayListOf(message)
                    }
                } while (moveToNext())
            }
            close()
        }
        mmsThread = Thread { mmsManager.getAllMMS(lastDate) }
        mmsThread.start()
    }

    fun getLabels() {
        done = sp.getInt("done", 0)
        index = sp.getInt("index", 0)
        senderNameMap = ContactsManager(mContext).getContactsHashMap()

        for ((_, msgs) in messages) total += msgs.size

        val messagesArray = messages.entries.toTypedArray()
        val number = messagesArray.size

        var saveThread: Thread? = null
        startTime = System.currentTimeMillis() - sp.getLong("timeTaken", 0)

        for (ind in index until number) {
            val sender = messagesArray[ind].component1()
            val msgs = messagesArray[ind].component2()
            var label: Int

            if (senderNameMap.containsKey(sender)) label = 0
            else nn.getPredictions(msgs).apply {
                senderToProbs[sender] = this
                label = toList().indexOf(maxOrNull())
            }

            saveThread?.join()
            saveThread = Thread { saveMessage(ind, sender, messages[sender]!!, label) }
            saveThread.start()
            updateProgress(msgs.size)
        }
        saveThread?.join()

        mmsThread.join()
        finish()
    }
}