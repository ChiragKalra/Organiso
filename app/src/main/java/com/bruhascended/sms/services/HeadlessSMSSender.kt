package com.bruhascended.sms.services

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.text.TextUtils
import androidx.room.Room
import com.bruhascended.sms.conversationDao
import com.bruhascended.sms.conversationSender
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.mainViewModel


class HeadlessSMSSender : Service() {

    private fun getRecipients(uri: Uri): String {
        val base: String = uri.schemeSpecificPart
        val pos = base.indexOf('?')
        return if (pos == -1) base else base.substring(0, pos)
    }

    private fun saveSent(add: String, sms: String) {
        val senderNameMap = ContactsManager(applicationContext).getContactsHashMap()

        val mDaos = Array(5){
            if (mainViewModel == null)
                Room.databaseBuilder(
                    applicationContext, ConversationDatabase::class.java,
                    applicationContext.resources.getString(labelText[it])
                ).build().manager()
            else mainViewModel!!.daos!![it]
        }

        var force = -1
        val probs = FloatArray(5){ if (it == 0) 1f else 0f }
        var conversation: Conversation? = null
        for (i in 0..4) {
            val got = mDaos[i].findBySender(add)
            if (got.isNotEmpty()) {
                force = got.first().forceLabel
                conversation = got.first()
                break
            }
        }

        if (conversation != null)
            for (j in 0..4) probs[j] += conversation.probs[j]
        var prediction = probs.indexOf(probs.max()!!)
        if (force > -1) prediction = force

        val con = Conversation (
            null,
            add,
            senderNameMap[add],
            "",
            true,
            System.currentTimeMillis(),
            sms,
            prediction,
            force,
            probs
        )
        if (mainViewModel != null) {
            for (j in 0..4) {
                val res = mainViewModel!!.daos!![j].findBySender(add)
                for (item in res) mainViewModel!!.daos!![j].delete(item)
            }
        } else {
            for (j in 0..4) {
                val temp = Room.databaseBuilder(
                    applicationContext, ConversationDatabase::class.java,
                    applicationContext.resources.getString(labelText[j])
                ).build().manager()
                val res = temp.findBySender(add)
                for (item in res) temp.delete(item)
            }
        }
        mDaos[prediction].insert(con)


        val mdb = if (conversationSender == add) conversationDao
        else Room.databaseBuilder(
            applicationContext, MessageDatabase::class.java, add
        ).build().manager()
        mdb.insert(
            Message(
                null,
                add,
                sms,
                2,
                System.currentTimeMillis(),
                prediction
            )
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent == null) return null

        val action = intent.action
        if (TelephonyManager.ACTION_RESPOND_VIA_MESSAGE != action) return null
        val extras = intent.extras ?: return null
        val message = extras.getString(Intent.EXTRA_TEXT)!!
        val intentUri: Uri = intent.data!!
        val recipients = getRecipients(intentUri)

        if (TextUtils.isEmpty(recipients) || TextUtils.isEmpty(message)) return null

        val destinations = TextUtils.split(recipients, ";")
        val smsManager: SmsManager = SmsManager.getDefault()
        for (destination in destinations) {
            smsManager.sendTextMessage(destination, null, message,
                null, null)
            saveSent(destination, message)
        }
        return null
    }
}