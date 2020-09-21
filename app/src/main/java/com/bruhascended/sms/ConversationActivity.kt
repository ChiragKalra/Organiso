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

package com.bruhascended.sms

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.analytics.AnalyticsLogger
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.services.MMSSender
import com.bruhascended.sms.services.SMSSender
import com.bruhascended.sms.ui.*
import com.bruhascended.sms.ui.conversastion.MessageListViewAdaptor
import com.bruhascended.sms.ui.conversastion.MessageMultiChoiceModeListener
import com.bruhascended.sms.ui.main.MainViewModel
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConversationActivity : AppCompatActivity() {
    private lateinit var mdb: MessageDao

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private lateinit var messages: List<Message>
    private var inputManager: InputMethodManager? = null

    private lateinit var mpm: MediaPreviewManager
    private lateinit var analyticsLogger: AnalyticsLogger

    private val selectMediaArg = 0
    private val selectMessageArg = 1


    private fun setupActivity(intent: Intent) {
        conversation = intent.getSerializableExtra("ye") as Conversation
        smsSender = SMSSender(this, conversation, sendButton)
        mmsSender = MMSSender(this, conversation, sendButton)
        analyticsLogger = AnalyticsLogger(this)
        activeConversationSender = conversation.sender

        setSupportActionBar(toolbar)
        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
        }

        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        if (conversation.id != null) {
            conversation.read = true
            mainViewModel.daos[conversation.label].update(conversation)
        }

        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        activeConversationDao = mdb

        mpm = MediaPreviewManager(
            this,
            videoView,
            imagePreview,
            seekBar,
            playPauseButton,
            videoPlayPauseButton,
            addMedia,
            selectMediaArg
        )

        sendButton.setOnClickListener {
            if (mpm.mmsType > 0) {
                mmsSender.sendMMS(messageEditText.text.toString(), mpm.mmsURI, mpm.mmsTypeString)
                messageEditText.setText("")
                mpm.hideMediaPreview()
            } else if (messageEditText.text.toString().trim() != "") {
                smsSender.sendSMS(messageEditText.text.toString())
                messageEditText.setText("")
            }
        }

        if (conversation.id == null) {
            messageEditText.setText(conversation.lastSMS)
            if (intent.data != null) mpm.showMediaPreview(intent)
            sendButton.callOnClick()
        }

        addMedia.setOnClickListener{
            mpm.loadMedia()
        }

        mdb.loadAll().observe(this, {
            if (it.count() > 0) it.last().apply {
                conversation.lastSMS = text
                conversation.time = time
                conversation.lastMMS = path != null
                mainViewModel.daos[conversation.label].update(conversation)
            }
            messages = it
            val editListAdapter = MessageListViewAdaptor(this, it)
            listView.apply {
                val recyclerViewState = onSaveInstanceState()!!
                if (adapter != null && lastVisiblePosition == adapter.count - 1 &&
                    childCount > 0 && getChildAt(childCount - 1).bottom <= height
                ) {
                    adapter = editListAdapter
                    smoothScrollToPosition(adapter.count - 1)
                } else {
                    adapter = editListAdapter
                    onRestoreInstanceState(recyclerViewState)
                }

                setMultiChoiceModeListener(
                    MessageMultiChoiceModeListener(mContext, this, mdb, conversation)
                )
            }
            progress.visibility = View.GONE
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)

        setContentView(R.layout.activity_conversation)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        setupActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name ?: conversation.sender
        if (item.itemId == android.R.id.home) onBackPressed()
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_5")
                        moveTo(conversation, 5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_4")
                        analyticsLogger.reportSpam(conversation)
                        moveTo(conversation, 4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_-1")
                        moveTo(conversation, -1)
                        Toast.makeText(mContext, "Conversation Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        finish()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_move -> {
                val choices = ArrayList<String>().apply {
                    for (i in 0..3) {
                        if (i != conversation.label) add(mContext.resources.getString(labelText[i]))
                    }
                }.toTypedArray()
                var selection = 0
                AlertDialog.Builder(mContext)
                    .setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select ->
                        selection = select + if (select >= conversation.label) 1 else 0
                    }
                    .setPositiveButton("Move") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_$selection")
                        moveTo(conversation, selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_search -> {
                startActivityForResult(
                    Intent(mContext, SearchActivity::class.java)
                        .putExtra("type", "messages"),
                    selectMessageArg
                )
                overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
            }
            R.id.action_mute -> {
                conversation.isMuted = !conversation.isMuted
                mainViewModel.daos[conversation.label].update(conversation)
                GlobalScope.launch {
                    delay(300)
                    runOnUiThread {
                        item.title = if (conversation.isMuted) "UnMute" else "Mute"
                    }
                }
            }
            R.id.action_call -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${conversation.sender}")
                startActivity(intent)
            }
            android.R.id.home -> onBackPressed()
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == selectMediaArg && data != null && data.data != null) {
            mpm.showMediaPreview(data)
        } else if (requestCode == selectMessageArg && resultCode == RESULT_OK && data != null) {
            val id = data.getLongExtra("ID", -1L)
            val yTranslate = data.getIntExtra("POS", 0)
            if (id == -1L) return
            val index = messages.indexOfFirst { m -> m.id==id }
            listView.setItemChecked(index, true)
            listView.clearFocus()
            listView.post {
                listView.setSelectionFromTop(index, yTranslate)
            }
        }
    }

    override fun onBackPressed() {
        startActivityIfNeeded(
            Intent(mContext, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
        )
        finish()
        super.onBackPressed()
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        if (menu == null) return super.onPrepareOptionsMenu(menu)
        val muteItem = menu.findItem(R.id.action_mute)
        val callItem = menu.findItem(R.id.action_call)

        muteItem.title = if (conversation.isMuted) "UnMute" else "Mute"
        callItem.isVisible = conversation.sender.first().isDigit()
        return super.onPrepareOptionsMenu(menu)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onPause() {
       activeConversationSender = null
        super.onPause()
    }

    override fun onResume() {
        activeConversationSender = conversation.sender
        super.onResume()
    }

    override fun onDestroy() {
        activeConversationSender = null
        super.onDestroy()
    }
}
