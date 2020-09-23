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

import android.content.Context
import androidx.room.Room
import com.bruhascended.sms.activeConversationDao
import com.bruhascended.sms.activeConversationSender
import com.bruhascended.sms.analytics.AnalyticsLogger
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.isMainViewModelNull
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ml.OrganizerModel
import com.bruhascended.sms.ml.getOtp
import com.bruhascended.sms.ui.main.MainViewModel

class IncomingSMSManager(
    private val mContext: Context
) {
    private val analyticsLogger = AnalyticsLogger(mContext)
    private val cm = ContactsManager(mContext)
    private val nn = OrganizerModel(mContext)
    private val senderNameMap = cm.getContactsHashMap()

    private fun initMainViewModel() {
        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }
    }

    private fun deletePrevious(rawNumber: String): Conversation? {
        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mainViewModel.daos[i].findBySender(rawNumber)
            if (got.isNotEmpty()) {
                conversation = got.first()
                for (item in got)
                    mainViewModel.daos[i].delete(item)
                break
            }
        }
        return conversation
    }

    fun putMessage(sender: String, body: String): Pair<Message, Conversation> {
        initMainViewModel()

        val rawNumber = cm.getRaw(sender)

        val message = Message(
            null,
            rawNumber,
            body,
            1,
            System.currentTimeMillis(),
            -1
        )

        var conversation = deletePrevious(rawNumber)

        var mProbs: FloatArray? = null
        val prediction = if (senderNameMap.containsKey(rawNumber)) 0
        else if (conversation != null && conversation.forceLabel != -1) conversation.forceLabel
        else if (!getOtp(body).isNullOrEmpty()) 2
        else {
            mProbs = nn.getPrediction(message)
            if (conversation != null) for (j in 0..4) mProbs[j] += conversation.probs[j]
            mProbs.toList().indexOf(mProbs.maxOrNull())
        }

        analyticsLogger.log("conversation_organised", "background")

        conversation = conversation?.apply {
            if (label != prediction) id = null
            read = false
            time = message.time
            lastSMS = message.text
            if (prediction == 0) forceLabel = 0
            label = prediction
            probs = mProbs ?: probs
            name = senderNameMap[rawNumber]
        } ?: Conversation(
            null,
            rawNumber,
            senderNameMap[rawNumber],
            "",
            false,
            message.time,
            message.text,
            prediction,
            if (prediction == 0) 0 else -1,
            FloatArray(5) {
                if (it == prediction) 1f else 0f
            }
        )

        mainViewModel.daos[prediction].insert(conversation)
        conversation.id = mainViewModel.daos[prediction].findBySender(rawNumber).first().id

        val mdb = if (activeConversationSender == rawNumber) activeConversationDao
        else Room.databaseBuilder(
            mContext, MessageDatabase::class.java, rawNumber
        ).build().manager()

        mdb.insert(message)

        return mdb.search(message.time).first() to conversation
    }

    fun close() {
        nn.close()
    }
}