package com.bruhascended.sms.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.bruhascended.sms.R
import com.bruhascended.sms.conversationDao
import com.bruhascended.sms.conversationSender
import com.bruhascended.sms.db.*
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ml.FeatureExtractor
import com.bruhascended.sms.ml.OrganizerModel
import com.bruhascended.sms.ui.start.StartViewModel
import java.lang.Integer.min
import kotlin.math.roundToInt


val labelText = arrayOf(
    R.string.tab_text_1,
    R.string.tab_text_2,
    R.string.tab_text_3,
    R.string.tab_text_4,
    R.string.tab_text_5,
    R.string.tab_text_6
)

/*
const val MESSAGE_TYPE_ALL = 0
const val MESSAGE_TYPE_INBOX = 1
const val MESSAGE_TYPE_SENT = 2
const val MESSAGE_TYPE_DRAFT = 3
const val MESSAGE_TYPE_OUTBOX = 4
const val MESSAGE_TYPE_FAILED = 5 // for failed outgoing messages
const val MESSAGE_TYPE_QUEUED = 6 // for messages to send later
*/

const val MESSAGE_CHECK_COUNT = 6

class SMSManager (context: Context) {
    private var mContext: Context = context

    private val messages = HashMap<String, ArrayList<Message>>()
    private val senderToProbs = HashMap<String, FloatArray>()
    private val labels = Array(5){ArrayList<String>()}

    private lateinit var senderNameMap: HashMap<String, String>

    private lateinit var mDaos: Array<ConversationDao>

    fun getMessages() {
        val sp = mContext.getSharedPreferences("local", Context.MODE_PRIVATE)
        val lastDate = sp.getLong("last", 0).toString()

        val cm = ContactsManager(mContext)

        val cursor = mContext.contentResolver.query(
            Uri.parse("content://sms/"),
            null,
            "date" + ">?",
            arrayOf(lastDate),
            "date ASC"
        )

        if (cursor != null && cursor.moveToFirst()) {
            val nameID = cursor.getColumnIndex("address")
            val messageID = cursor.getColumnIndex("body")
            val dateID = cursor.getColumnIndex("date")
            val typeID = cursor.getColumnIndex("type")

            do {
                var name = cursor.getString(nameID)
                if (name != null) {
                    name = cm.getRaw(name)
                    val message = Message(
                        null,
                        name,
                        cursor.getString(messageID),
                        cursor.getString(typeID).toInt(),
                        cursor.getString(dateID).toLong(),
                        -1
                    )

                    if (messages.containsKey(name)) {
                        messages[name]?.add(message)
                    } else {
                        messages[name] = arrayListOf(message)
                    }
                }
            } while (cursor.moveToNext())

            cursor.close()
        }
    }

    fun putMessage(sender: String, body: String) {
        val name = ContactsManager(mContext).getRaw(sender)
        val message = Message(
            null,
            name,
            body,
            1,
            System.currentTimeMillis(),
            -1
        )

        if (messages.containsKey(name)) {
            messages[name]?.add(message)
        } else {
            messages[name] = arrayListOf(message)
        }
    }

    fun getLabels(pageViewModel: StartViewModel? = null) {
        val nn = OrganizerModel(mContext)
        val fe = FeatureExtractor(mContext)

        var timeFeat = 0L
        var timeML = 0L

        val startTime = System.currentTimeMillis()

        senderNameMap = ContactsManager(mContext).getContactsHashMap()

        var total = 0
        for ((_, msgs) in messages) {
            total += min(msgs.size, MESSAGE_CHECK_COUNT)
        }

        mDaos = Array(5){
            if (mainViewModel == null)
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).build().manager()
            else mainViewModel!!.daos[it]
        }

        var done = 0
        for ((sender, msgs) in messages) {
            if (senderNameMap.containsKey(sender)) {
                labels[0].add(sender)
            } else {
                var yeah = System.currentTimeMillis()
                val features = fe.getFeatureMatrix(msgs)
                timeFeat += System.currentTimeMillis()-yeah
                yeah = System.currentTimeMillis()
                val probs = nn.getPredictions(msgs, features)
                timeML += System.currentTimeMillis()-yeah


                var force = -1
                var conversation: Conversation? = null
                for (i in 0..4) {
                    val got = mDaos[i].findBySender(sender)
                    if (got.isNotEmpty()) {
                        force = got.first().forceLabel
                        conversation = got.first()
                        break
                    }
                }

                if (conversation != null)
                    for (j in 0..4) probs[j] += conversation.probs[j]
                val prediction = probs.indexOf(probs.max()!!)
                senderToProbs[sender] = probs

                if (force != -1)
                    labels[force].add(sender)
                else if ((!sender.first().isDigit()) && prediction == 0)
                    labels[1].add(sender)
                else
                    labels[prediction].add(sender)
            }
            done += min(msgs.size, MESSAGE_CHECK_COUNT)
            val per = done * 100f / total
            pageViewModel?.progress?.postValue(per.roundToInt())

            val eta = (System.currentTimeMillis()-startTime) * (100/per-1)
            pageViewModel?.eta?.postValue(eta.toLong())
        }
    }

    fun saveMessages() {
        for (i in 0..4) {
            for (conversation in labels[i]) {
                if (mainViewModel != null) {
                    for (j in 0..4) {
                        val res = mainViewModel!!.daos[j].findBySender(conversation)
                        if (res.isNotEmpty()) {
                            mainViewModel!!.daos[i].delete(res[0])
                        }
                    }
                }

                mDaos[i].insert(
                    Conversation(
                        null,
                        conversation,
                        senderNameMap[conversation],
                        "",
                        true,
                        messages[conversation]!!.last().time,
                        messages[conversation]!!.last().text,
                        i,
                        -1,
                        senderToProbs[conversation]?: FloatArray(5){
                            if (it == 0) 1f else 0f
                        }
                    )
                )

                val mdb = if (conversationSender == conversation) conversationDao
                else Room.databaseBuilder(
                    mContext, MessageDatabase::class.java, conversation
                ).build().manager()
                for (message in messages[conversation]!!)
                    mdb.insert(message)
            }
        }
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE)
            .edit().putLong("last", System.currentTimeMillis()).apply()
    }
}