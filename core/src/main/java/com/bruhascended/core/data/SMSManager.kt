package com.bruhascended.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.model.OrganizerModel
import com.bruhascended.core.db.MainDaoProvider
import com.bruhascended.core.model.getOtp

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

class SMSManager (private val mContext: Context) {

    class Number (
        val address: String,
        val clean: String
    ) {
        override fun equals(other: Any?) : Boolean {
            if (other !is Number) return false
            return clean == other.clean
        }

        override fun hashCode(): Int {
            return clean.hashCode()
        }
    }

    private val cm = ContactsManager(mContext)
    private val analyticsLogger = AnalyticsLogger(mContext)
    private val mMainDaoProvider = MainDaoProvider(mContext)
    private val mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext)

    private val messages = HashMap<Number, ArrayList<Message>>()
    private val senderToProbs = HashMap<String, FloatArray>()

    private var isWorkingAfterInit = false
    private var done = 0
    private var index = 0
    private var total = 0

    private var timeTaken = 0L
    private var startTime = 0L

    private lateinit var senderNameMap: HashMap<String, String>
    private lateinit var mmsManager: MMSManager
    private lateinit var nn: OrganizerModel
    private lateinit var mmsThread: Thread

    var onStatusChangeListener: (Int) -> Unit = {}
    var onProgressListener: (Float) -> Unit = {}
    var onEtaChangeListener: (Long) -> Unit = {}

    private fun finish() {
        onStatusChangeListener(2)

        if (!isWorkingAfterInit) nn.close()
        senderNameMap.clear()
        senderToProbs.clear()

        mPrefs.edit()
            .remove(KEY_RESUME_INDEX)
            .remove(KEY_DONE_COUNT)
            .putLong(KEY_RESUME_DATE, System.currentTimeMillis())
            .apply()

        isWorkingAfterInit = false
    }

    // Init heavy objects here
    private fun initLate() {
        if (!::senderNameMap.isInitialized || senderNameMap.isEmpty()) {
            senderNameMap = cm.getContactsHashMap()
            nn = OrganizerModel(mContext)
            mmsManager = MMSManager(mContext, senderNameMap)
        }
    }

    private fun updateProgress(size: Int) {
        done += size
        timeTaken = (System.currentTimeMillis()-startTime)
        val per = done * 100f / total
        val eta = (System.currentTimeMillis()-startTime) * (100/per-1)
        onProgressListener(per)
        onEtaChangeListener(eta.toLong())

        analyticsLogger.log(EVENT_CONVERSATION_ORGANISED, PARAM_INIT)
    }

    private fun deletePrevious(rawNumber: String): Conversation? {
        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mMainDaoProvider.getMainDaos()[i].findBySender(rawNumber)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got)
                    mMainDaoProvider.getMainDaos()[i].delete(item)
            }
        }
        return conversation
    }

    private fun saveMessages(ind: Int, number: Number, messages: ArrayList<Message>, label: Int) {
        if (number.clean.isBlank()) return

        val conversation = deletePrevious(number.clean)

        if (conversation != null) {
            conversation.apply {
                if (label != this.label) id = null
                read = !isWorkingAfterInit
                time = messages.last().time
                lastSMS =  messages.last().text
                lastMMS = false
                if (label == LABEL_PERSONAL) forceLabel = LABEL_PERSONAL
                name = senderNameMap[number.clean]
                mMainDaoProvider.getMainDaos()[label].insert(this)
            }
        } else {
            val con = Conversation(
                number.address,
                number.clean,
                senderNameMap[number.clean],
                read = !isWorkingAfterInit,
                time = messages.last().time,
                lastSMS = messages.last().text,
                label = label,
                forceLabel = if (label == LABEL_PERSONAL) LABEL_PERSONAL else LABEL_NONE,
                probabilities = senderToProbs[number.clean] ?:
                    FloatArray(5) { if (it == LABEL_PERSONAL) 1f else 0f }
            )

            mMainDaoProvider.getMainDaos()[label].insert(con)
        }

        MessageDbFactory(mContext).of(number.clean).apply {
            manager().insertAll(messages.toList())
            close()
        }

        mPrefs.edit()
            .putInt(KEY_RESUME_INDEX, ind + 1)
            .putInt(KEY_DONE_COUNT, done)
            .putLong(KEY_TIME_TAKEN, timeTaken)
            .apply()
    }


    fun getMessages() {
        initLate()

        val lastDate = mPrefs.getLong(KEY_RESUME_DATE, 0).toString()

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
                    val name = getString(nameID)
                    val messageContent = getString(messageID)
                    if (name != null && !messageContent.isNullOrEmpty()) {

                        val number = Number(name, cm.getClean(name))
                        val message = Message(
                            messageContent, getString(typeID).toInt(), getString(dateID).toLong()
                        )
                        if (messages.containsKey(number)) messages[number]?.add(message)
                        else messages[number] = arrayListOf(message)
                    }
                } while (moveToNext())
            }
            close()
        }
        mmsThread = Thread { mmsManager.getAllMMS(lastDate) }
        mmsThread.start()
    }

    fun getLabels() {
        done = mPrefs.getInt(KEY_DONE_COUNT, 0)
        index = mPrefs.getInt(KEY_RESUME_INDEX, 0)

        for ((_, msgs) in messages) total += msgs.size

        val messagesArray = messages.entries.toTypedArray()
        val totalNumber = messagesArray.size

        var saveThread: Thread? = null
        startTime = System.currentTimeMillis() - mPrefs.getLong(KEY_TIME_TAKEN, 0)

        for (ind in index until totalNumber) {
            val number = messagesArray[ind].component1()
            val msgs = messagesArray[ind].component2()
            var label: Int

            if (senderNameMap.containsKey(number.clean)) label = 0
            else nn.getPredictions(msgs).apply {
                senderToProbs[number.clean] = this
                label = toList().indexOf(maxOrNull())
            }

            saveThread?.join()
            saveThread = Thread { saveMessages(ind, number, messages[number]!!, label) }
            saveThread.start()
            updateProgress(msgs.size)
        }
        saveThread?.join()

        mmsThread.join()
        finish()
    }

    fun putMessage(number: String, body: String, active: Boolean): Pair<Message, Conversation>? {
        initLate()

        val rawNumber = cm.getClean(number)
        val message = Message(body, MESSAGE_TYPE_INBOX, System.currentTimeMillis())

        var conversation = deletePrevious(rawNumber)

        var mProbs: FloatArray? = null
        val prediction = if (senderNameMap.containsKey(rawNumber)) LABEL_PERSONAL
        else if (conversation != null && conversation.forceLabel != LABEL_NONE) conversation.forceLabel
        else if (!getOtp(body).isNullOrEmpty()) LABEL_TRANSACTIONS
        else {
            mProbs = nn.getPrediction(message)
            if (conversation != null)
                for (j in 0..4) mProbs[j] += conversation.probabilities[j]
            mProbs.toList().indexOf(mProbs.maxOrNull())
        }

        analyticsLogger.log(EVENT_CONVERSATION_ORGANISED, PARAM_BACKGROUND)

        conversation = conversation?.apply {
            if (label != prediction) id = null
            read = false
            time = message.time
            lastSMS = message.text
            if (prediction == LABEL_PERSONAL) forceLabel = LABEL_PERSONAL
            label = prediction
            probabilities = mProbs ?: probabilities
            name = senderNameMap[rawNumber]
        } ?: Conversation(
            number,
            rawNumber,
            senderNameMap[rawNumber],
            read = false,
            time = message.time,
            lastSMS = message.text,
            label = prediction,
            forceLabel = if (prediction == LABEL_PERSONAL) LABEL_PERSONAL else LABEL_NONE
        )

        mMainDaoProvider.getMainDaos()[prediction].insert(conversation)
        if (conversation.id == null)
            conversation.id = mMainDaoProvider.getMainDaos()[prediction].findBySender(rawNumber).first().id

        return if (active) {
            mContext.sendBroadcast(Intent(ACTION_NEW_MESSAGE).apply{
                putExtra(EXTRA_MESSAGE, message)
                setPackage(mContext.applicationInfo.packageName)
            })
            null
        } else {
            val mdb = MessageDbFactory(mContext).of(rawNumber)
            mdb.manager().insert(message)
            val a = mdb.manager().search(message.time).first() to conversation
            mdb.close()
            a
        }
    }

    fun updateAsync() {
        if (isWorkingAfterInit) return
        isWorkingAfterInit = true
        Thread {
            getMessages()
            getLabels()
        }.start()
    }

    fun updateSync() {
        if (isWorkingAfterInit) return
        isWorkingAfterInit = true
        getMessages()
        getLabels()
    }

    fun close() {
        nn.close()
    }

}