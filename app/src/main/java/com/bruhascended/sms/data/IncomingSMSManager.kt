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
import com.bruhascended.sms.analytics.AnalyticsLogger
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.*
import com.bruhascended.sms.ml.OrganizerModel
import com.bruhascended.sms.ui.main.MainViewModel

class IncomingSMSManager(context: Context) {
    private var mContext: Context = context

    fun putMessage(sender: String, body: String): Pair<Message, Conversation> {
        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        val rawNumber = ContactsManager(mContext).getRaw(sender)
        val nn = OrganizerModel(mContext)
        val senderNameMap = ContactsManager(mContext).getContactsHashMap()

        val message = Message(
            null,
            rawNumber,
            body,
            1,
            System.currentTimeMillis(),
            -1
        )

        AnalyticsLogger(mContext).log("conversation_organised", "background")

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

        var mProbs: FloatArray? = null
        val prediction = if (senderNameMap.containsKey(rawNumber)) 0
        else if (conversation != null && conversation.forceLabel != -1) conversation.forceLabel
        else {
            mProbs = nn.getPrediction(message)
            if (conversation != null) for (j in 0..4) conversation.probs[j] += mProbs[j]
            mProbs.toList().indexOf(mProbs.maxOrNull())
        }
        nn.close()


        conversation = if (conversation != null) {
            conversation.apply {
                read = false
                time = message.time
                lastSMS = message.text
                label = prediction
                probs = mProbs ?: probs
                name = senderNameMap[rawNumber]
                mainViewModel.daos[prediction].update(this)
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
                prediction,
                -1,
                FloatArray(5) {
                    if (it == prediction) 1f else 0f
                }
            )
            mainViewModel.daos[prediction].insert(con)
            con.id = mainViewModel.daos[prediction].findBySender(rawNumber).first().id
            con
        }

        val mdb = if (conversationSender == rawNumber) conversationDao
        else Room.databaseBuilder(
            mContext, MessageDatabase::class.java, rawNumber
        ).build().manager()

        mdb.insert(message)

        return mdb.search(message.time).first() to conversation
    }
}