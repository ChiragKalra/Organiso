@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.sms

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDao
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.main.MessageListViewAdaptor


class ConversationActivity : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation
    private lateinit var messageEditText: EditText
    private lateinit var mdb: MessageDao


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        mContext = this
        conversation = intent.getSerializableExtra("ye") as Conversation

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.title = conversation.name ?: conversation.sender

        val listView: ListView = findViewById(R.id.messageListView)
        val sendLayout: LinearLayout = findViewById(R.id.sendLayout)
        val notSupport: TextView = findViewById(R.id.notSupported)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.GONE
            notSupport.visibility = TextView.VISIBLE
        }

        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()

        mdb.loadAll().observe(this, Observer<List<Message>> {
            listView.adapter = MessageListViewAdaptor(this, it)

            listView.onItemLongClickListener = AdapterView.OnItemLongClickListener{
                    _: AdapterView<*>, _: View, i: Int, _: Long ->
                (mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("copied sms", it[i].text))
                Toast.makeText(mContext, "Copied To Clipboard", Toast.LENGTH_LONG).show()
                true
            }
        })

        val sendButton: ImageButton = findViewById(R.id.sendButton)
        messageEditText = findViewById(R.id.messageEditText)

        sendButton.setOnClickListener{
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.SEND_SMS),
                    12312
                )
            } else sendSMS()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to block ${conversation.sender}?")
                    .setPositiveButton("Move") { dialog, _ ->
                        moveTo(conversation, 5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to report ${conversation.sender} as spam?")
                    .setPositiveButton("Move") { dialog, _ ->
                        moveTo(conversation, 4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_move -> {
                val choices = Array(4){mContext.resources.getString(labelText[it])}

                var selection = conversation.label
                AlertDialog.Builder(mContext).setTitle("Move ${conversation.sender} to ")
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

    private fun sendSMS() {
        val smsText = messageEditText.text.toString()
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

        conversation.time = date
        conversation.lastSMS = smsText

        Thread ( Runnable {
            mainViewModel!!.daos[conversation.label].update(conversation)
        }).start()

        Toast.makeText(mContext, "SMS sent", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            12312 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    sendSMS()
                else Toast.makeText(mContext, "Insufficient Permissions.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
