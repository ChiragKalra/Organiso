@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.sms

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDao
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.listViewAdapter.MessageListViewAdaptor


class ConversationActivity : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation
    private lateinit var messageEditText: EditText
    private lateinit var mdb: MessageDao


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        mContext = this

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        val sendLayout: LinearLayout = findViewById(R.id.sendLayout)
        val sendButton: ImageButton = findViewById(R.id.sendButton)
        val listView: ListView = findViewById(R.id.messageListView)
        val notSupport: TextView = findViewById(R.id.notSupported)

        messageEditText = findViewById(R.id.messageEditText)

        conversation = intent.getSerializableExtra("ye") as Conversation

        supportActionBar!!.title = conversation.name ?: conversation.sender

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.GONE
            notSupport.visibility = TextView.VISIBLE
        }

        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()

        if (conversation.id == null) {
            sendSMS()
        }

        mdb.loadAll().observe(this, Observer<List<Message>> {
            listView.adapter =
                MessageListViewAdaptor(
                    this,
                    it
                )

            listView.onItemLongClickListener =
                AdapterView.OnItemLongClickListener { _: AdapterView<*>, _: View, i: Int, _: Long ->
                    (mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("copied sms", it[i].text))
                    Toast.makeText(mContext, "Copied To Clipboard", Toast.LENGTH_LONG).show()
                    true
                }
        })

        sendButton.setOnClickListener {
            if (messageEditText.text.toString().trim() != "") sendSMS()
        }
    }

    private fun sendSMS() {
        val smsText = if (conversation.id != null)
            messageEditText.text.toString() else conversation.lastSMS
        val date = System.currentTimeMillis()

        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(conversation.sender, null,
            smsText, null, null)
        messageEditText.text.clear()

        mdb.insert(
            Message(
                null,
                conversation.sender,
                smsText,
                2,
                date,
                0
            )
        )

        Thread ( Runnable {
            if (conversation.id == null) {
                var found = false
                for (i in 0..4) {
                    val res = mainViewModel!!.daos[i].findBySender(conversation.sender)
                    if (res.isNotEmpty()) {
                        found = true
                        conversation = res[0]
                        break
                    }
                }

                conversation.time = date
                conversation.lastSMS = smsText

                if (found)
                    mainViewModel!!.daos[conversation.label].update(conversation)
                else
                    mainViewModel!!.daos[conversation.label].insert(conversation)
            } else {
                conversation.time = date
                conversation.lastSMS = smsText

                mainViewModel!!.daos[conversation.label].update(conversation)
            }
        }).start()

        Toast.makeText(mContext, "SMS sent", Toast.LENGTH_LONG).show()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name?: conversation.sender
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        moveTo(conversation, 5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        moveTo(conversation, 4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        moveTo(conversation, -1)
                        Toast.makeText(mContext, "Conversation Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        finish()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_move -> {
                val choices = Array(4){mContext.resources.getString(labelText[it])}

                var selection = conversation.label
                AlertDialog.Builder(mContext).setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select -> selection = select}
                    .setPositiveButton("Move") { dialog, _ ->
                        moveTo(conversation, selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
        }
        return false
    }
}
