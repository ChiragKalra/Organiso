package com.bruhascended.sms.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import com.bruhascended.db.*
import com.bruhascended.sms.*
import com.bruhascended.sms.R
import com.bruhascended.sms.ml.OrganizerModel
import com.bruhascended.sms.ui.start.StartViewModel
import com.google.firebase.analytics.FirebaseAnalytics
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

const val MESSAGE_CHECK_COUNT = 6

class SMSManager(context: Context) {
    private var mContext: Context = context

    private val messages = HashMap<String, ArrayList<Message>>()
    private val senderToProbs = HashMap<String, FloatArray>()
    private val senderForce = HashMap<String, Int>()
    private val labels = Array(5){ArrayList<String>()}

    private var completedSenderIndex: Int = 0
    private var completedMessage: Int = 0
    private var timeTaken: Long = 0
    private val savedSenders: MutableSet<String> = mutableSetOf()

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
                val messageContent = cursor.getString(messageID)
                if (name != null && messageContent != null && messageContent != "") {
                    name = cm.getRaw(name)
                    val message = Message(
                        null,
                        name,
                        messageContent,
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
        val sp = mContext.getSharedPreferences("local", Context.MODE_PRIVATE)

        var timeFeat = 0L
        var timeML = 0L

        val startTime = System.currentTimeMillis() - sp.getLong("timeTaken", 0)

        senderNameMap = ContactsManager(mContext).getContactsHashMap()

        var total = 0
        for ((_, msgs) in messages) {
            total += min(msgs.size, MESSAGE_CHECK_COUNT)
        }

        mDaos = Array(5){
            if (isMainViewModelNull())
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).build().manager()
            else mainViewModel.daos[it]
        }

        var done = sp.getInt("done", 0)
        val index = sp.getInt("index", 0)
        val messagesArray = messages.entries.toTypedArray()
        val number = messagesArray.size

        val firebaseAnalytics = FirebaseAnalytics.getInstance(mContext)

        for (ind in index until number) {
            val sender = messagesArray[ind].component1()
            val msgs = messagesArray[ind].component2()

            if (senderNameMap.containsKey(sender)) {
                labels[0].add(sender)
            } else {
                var yeah = System.currentTimeMillis()
                timeFeat += System.currentTimeMillis()-yeah
                yeah = System.currentTimeMillis()
                val probs = nn.getPredictions(msgs)
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
                val prediction = probs.toList().indexOf(probs.maxOrNull())
                senderToProbs[sender] = probs
                senderForce[sender] = force

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

            completedSenderIndex = ind
            completedMessage = done
            timeTaken = (System.currentTimeMillis()-startTime)

            val bundle = Bundle()
            bundle.putString(
                FirebaseAnalytics.Param.METHOD,
                if (pageViewModel == null) "background" else "init"
            )
            firebaseAnalytics.logEvent("conversation_organised", bundle)
        }
    }

    fun destroy() {
        mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
            .putInt("index", completedSenderIndex + 1)
            .putInt("done", completedMessage)
            .putLong("timeTaken", timeTaken)
            .apply()
        Thread{saveMessages(false)}.start()
    }

    fun saveMessages(done: Boolean = true): ArrayList<Pair<Message, Conversation>> {
        val returnMessages = ArrayList<Pair<Message, Conversation>>()
        for (i in 0..4) {
            for (conversation in labels[i].toTypedArray()) {
                if (!savedSenders.contains(conversation)) {
                    var preConversation: Conversation? = null
                    if (isMainViewModelNull()) {
                        for (j in 0..4) {
                            val temp = Room.databaseBuilder(
                                mContext, ConversationDatabase::class.java,
                                mContext.resources.getString(labelText[j])
                            ).build().manager()
                            val res = temp.findBySender(conversation)
                            if (res.isNotEmpty()) {
                                preConversation = res.first()
                                for (item in res.slice(1 until res.size))
                                    temp.delete(item)
                                break
                            }
                        }
                    } else {
                        for (j in 0..4) {
                            val res = mainViewModel.daos[j].findBySender(conversation)
                            if (res.isNotEmpty()) {
                                preConversation = res.first()
                                for (item in res.slice(1 until res.size))
                                    mainViewModel.daos[j].delete(item)
                                break
                            }
                        }
                    }
                    val con = if (preConversation != null) {
                        preConversation.apply {
                            read = false
                            time = messages[conversation]!!.last().time
                            lastSMS = messages[conversation]!!.last().text
                            label = i
                            forceLabel = senderForce[conversation] ?: -1
                            probs = senderToProbs[conversation] ?: FloatArray(5) {
                                if (it == 0) 1f else 0f
                            }
                            name = senderNameMap[conversation]
                            mDaos[i].update(preConversation)
                        }
                        preConversation
                    } else {
                        val con = Conversation(
                            null,
                            conversation,
                            senderNameMap[conversation],
                            "",
                            false,
                            messages[conversation]!!.last().time,
                            messages[conversation]!!.last().text,
                            i,
                            senderForce[conversation] ?: -1,
                            senderToProbs[conversation] ?: FloatArray(5) {
                                if (it == 0) 1f else 0f
                            }
                        )
                        mDaos[i].insert(con)
                        con.id = mDaos[i].findBySender(conversation).first().id
                        con
                    }

                    savedSenders.add(conversation)

                    val mdb = if (conversationSender == conversation) conversationDao
                    else Room.databaseBuilder(
                        mContext, MessageDatabase::class.java, conversation
                    ).build().manager()

                    for (message in messages[conversation]!!) {
                        mdb.insert(message)
                        if (conversationSender != conversation)
                            returnMessages.add(message to con)
                    }
                }
            }
        }
        if (done) {
            mContext.getSharedPreferences("local", Context.MODE_PRIVATE).edit()
                .putLong("last", System.currentTimeMillis())
                .putInt("index", 0)
                .putInt("done", 0)
                .apply()
        }
        return returnMessages
    }
}