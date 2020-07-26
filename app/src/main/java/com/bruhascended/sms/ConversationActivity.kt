@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.*
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.ui.listViewAdapter.MessageListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import kotlinx.android.synthetic.main.activity_conversation.*
import java.util.*


var conversationSender: String? = null
lateinit var conversationDao: MessageDao

class ConversationActivity : AppCompatActivity() {
    lateinit var mdb: MessageDao

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var messageEditText: EditText
    private lateinit var searchLayout: LinearLayout
    private lateinit var backButton: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var listView: ListView
    private lateinit var loading: ProgressBar
    private lateinit var sendLayout: LinearLayout
    private lateinit var notSupport: TextView
    private lateinit var sendButton: ImageButton
    private var inputManager: InputMethodManager? = null

    private fun addSmsToDb(smsText: String, date: Long) {
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
    }

    private fun sendSMS() {
        Toast.makeText(
            baseContext,
            "Sending",
            Toast.LENGTH_SHORT
        ).show()
        sendButton.isEnabled = false
        val smsManager = SmsManager.getDefault()
        val smsText = if (conversation.id != null) messageEditText.text.toString() else conversation.lastSMS
        val date = System.currentTimeMillis()

        val sentPI = PendingIntent.getBroadcast(this, 0, Intent("SENT"), 0)
        val deliveredPI = PendingIntent.getBroadcast(this, 0, Intent("DELIVERED"), 0)

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Toast.makeText(
                            baseContext,
                            "SMS sent",
                            Toast.LENGTH_SHORT
                        ).show()
                        addSmsToDb(smsText, date)
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> Toast.makeText(
                        baseContext,
                        "Service provider error",
                        Toast.LENGTH_SHORT
                    ).show()
                    SmsManager.RESULT_ERROR_NO_SERVICE -> Toast.makeText(
                        baseContext,
                        "No service",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                sendButton.isEnabled = true
                unregisterReceiver(this)
            }
        }, IntentFilter("SENT"))
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(arg0: Context?, arg1: Intent?) {
                when (resultCode) {
                    Activity.RESULT_OK -> Toast.makeText(
                        baseContext,
                        "SMS delivered",
                        Toast.LENGTH_SHORT
                    ).show()
                    Activity.RESULT_CANCELED -> Toast.makeText(
                        baseContext,
                        "SMS not delivered",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                unregisterReceiver(this)
            }
        }, IntentFilter("DELIVERED"))

        smsManager.sendTextMessage(conversation.sender, null, smsText, sentPI, deliveredPI)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        if (mainViewModel == null) {
            mainViewModel = MainViewModel()

            mainViewModel!!.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        mContext = this

        notSupport = findViewById(R.id.notSupported)
        val toolbar: Toolbar = findViewById(R.id.toolbar)

        sendLayout = findViewById(R.id.sendLayout)
        listView = findViewById(R.id.messageListView)
        messageEditText = findViewById(R.id.messageEditText)
        backButton = findViewById(R.id.cancelSearch)
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        loading = findViewById(R.id.progress)
        sendButton = findViewById(R.id.sendButton)
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        conversation = intent.getSerializableExtra("ye") as Conversation

        conversationSender = conversation.sender

        setSupportActionBar(toolbar)
        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupport.visibility = TextView.VISIBLE
        }

        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()

        conversationDao = mdb

        if (conversation.id == null) sendSMS()
        sendButton.setOnClickListener {
            if (messageEditText.text.toString().trim() != "")
                sendSMS()
        }

        mdb.loadAll().observe(this, Observer<List<Message>> {
            listView.adapter = MessageListViewAdaptor(this, it)
            listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, i, _ ->
                (mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("copied sms", it[i].text))
                Toast.makeText(mContext, "Copied To Clipboard", Toast.LENGTH_LONG).show()
                true
            }
            progress.visibility = View.GONE
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onDestroy() {
        conversationSender = null
        super.onDestroy()
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
            R.id.action_search -> {
                showSearchLayout()
                backButton.setOnClickListener{ hideSearchLayout() }
            }
        }
        return false
    }

    private fun showSearchLayout() {
        searchLayout.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        searchEditText.requestFocus()
                        inputManager?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                        notSupport.visibility = TextView.GONE
                        sendLayout.visibility = LinearLayout.GONE
                    }
                })
        }

        searchEditText.doOnTextChanged { _, _, _, _ ->
            val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
            progress.visibility = View.VISIBLE
            if (key.isNotEmpty()) {
                listView.adapter = MessageListViewAdaptor(mContext, mdb.search("%${key}%"))
            }
            progress.visibility = View.GONE
        }
    }

    private fun hideSearchLayout() {
        progress.visibility = View.VISIBLE
        if (!conversation.sender.first().isDigit()) {
            notSupport.visibility = TextView.VISIBLE
        } else {
            sendLayout.visibility = LinearLayout.VISIBLE
        }

        searchLayout.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    searchLayout.visibility = View.GONE
                    inputManager?.hideSoftInputFromWindow(backButton.windowToken, 0)
                }
            }).start()


        mdb.loadAll().observe(mContext as AppCompatActivity, object: Observer<List<Message>> {
            override fun onChanged(t: List<Message>?) {
                listView.adapter = MessageListViewAdaptor(mContext, t!!)
                progress.visibility = View.GONE
                mdb.loadAll().removeObserver(this)
            }
        })
    }
}
