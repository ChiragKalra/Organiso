package com.bruhascended.sms

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.bruhascended.sms.analytics.AnalyticsLogger
import com.bruhascended.sms.data.SMSManager.Companion.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.services.MMSSender
import com.bruhascended.sms.services.SMSSender
import com.bruhascended.sms.ui.common.ListSelectionManager
import com.bruhascended.sms.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.sms.ui.common.MediaPreviewManager
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import com.bruhascended.sms.ui.conversastion.MessageRecyclerAdaptor
import com.bruhascended.sms.ui.conversastion.MessageSelectionListener
import com.bruhascended.sms.ui.conversastion.SearchActivity
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


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

@Suppress("UNCHECKED_CAST")
class ConversationActivity : AppCompatActivity() {

    companion object {
        const val selectMediaArg = 0
        const val selectMessageArg = 1
    }

    private lateinit var mdb: MessageDao

    private lateinit var messages: List<Message>
    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var mAdaptor: MessageRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager

    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private var inputManager: InputMethodManager? = null

    private lateinit var mpm: MediaPreviewManager
    private lateinit var analyticsLogger: AnalyticsLogger
    private lateinit var selectionManager: ListSelectionManager<Message>


    private fun init() {
        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        conversation = intent.getSerializableExtra("ye") as Conversation
        smsSender = SMSSender(this, conversation, sendButton)
        mmsSender = MMSSender(this, conversation, sendButton)
        analyticsLogger = AnalyticsLogger(this)
        requireMainViewModel(this)
        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        activeConversationDao = mdb
        activeConversationSender = conversation.sender

        mpm = MediaPreviewManager(
            this,
            videoView,
            imagePreview,
            seekBar,
            playPauseButton,
            videoPlayPauseButton,
            addMedia
        )
    }

    private var scroll = true
    private fun setupRecycler() {
        activeConversationDao.loadAll().observe(this, {
            messages = it
            if (scroll) {
                scroll = false
                val id = intent?.getLongExtra("ID", -1L) ?: -1L
                if (id != -1L) {
                    recyclerView.postDelayed( {
                        scrollToItem(id)
                    }, 200)
                }
            }
        })

        val flow = Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 30,
                maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
            )
        ) {
            mdb.loadAllPaged()
        }.flow.cachedIn(lifecycleScope)

        mAdaptor = MessageRecyclerAdaptor(mContext)
        val mListener =  MessageSelectionListener(mContext, conversation)
        selectionManager = ListSelectionManager(
            mContext as AppCompatActivity,
            mAdaptor as SelectionRecyclerAdaptor<Message, RecyclerView.ViewHolder>,
            mListener
        )
        mAdaptor.selectionManager = selectionManager
        mListener.selectionManager = selectionManager
        recyclerView.apply {
            conversationLayout.doOnLayout {
                layoutParams.height = sendLayout.top - appBarLayout.bottom
            }
            mLayoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
                reverseLayout = true
            }
            layoutManager = mLayoutManager
            adapter = mAdaptor
            recyclerView.edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
        }

        lifecycleScope.launch {
            flow.collectLatest {
                mAdaptor.submitData(it)
            }
        }

        mAdaptor.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    recyclerView.scrollToPosition(0)
                } else {
                    super.onItemRangeInserted(positionStart, itemCount)
                }
            }
        })
    }

    private fun trackLastMessage() {
        mdb.loadLast().observe(this, {
            if (it != null) {
                conversation.lastSMS = it.text
                conversation.time = it.time
                conversation.lastMMS = it.path != null
                mainViewModel.daos[conversation.label].update(conversation)
            } else {
                mainViewModel.daos[conversation.label].delete(conversation)
            }
        })
    }

    private fun scrollToItem(id: Long) {
        val index = messages.indexOfFirst { m -> m.id==id }
        recyclerView.apply {
            scrollToPosition(index)
            selectionManager.toggleItem(index)
            mAdaptor.notifyItemChanged(index)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                        smoothScrollBy(0, measuredHeight / 2 - getChildAt(index).bottom)
                        removeOnScrollListener(this)
                    }
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_conversation)
        setSupportActionBar(toolbar)
        init()

        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
        }

        val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (notif in notificationManager.activeNotifications) {
            if (notif.groupKey == conversation.sender) {
                notificationManager.cancel(notif.id)
            }
        }

        if (conversation.id != null) {
            conversation.read = true
            mainViewModel.daos[conversation.label].update(conversation)
        } else {
            messageEditText.setText(conversation.lastSMS)
            if (intent.data != null) mpm.showMediaPreview(intent)
            sendButton.callOnClick()
        }

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

        addMedia.setOnClickListener{
            mpm.loadMedia()
        }

        setupRecycler()
        trackLastMessage()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name ?: conversation.sender
        if (item.itemId == android.R.id.home) onBackPressed()
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_5")
                        conversation.moveTo(5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_4")
                        analyticsLogger.reportSpam(conversation)
                        conversation.moveTo(4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_-1")
                        conversation.moveTo(-1, mContext)
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
                        conversation.moveTo(selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_search -> {
                startActivityForResult(
                    Intent(mContext, SearchActivity::class.java),
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
            android.R.id.home -> {
                startActivityIfNeeded(
                    Intent(mContext, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
                )
                finish()
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == selectMediaArg && data != null && data.data != null) {
            mpm.showMediaPreview(data)
        } else if (requestCode == selectMessageArg && resultCode == RESULT_OK && data != null) {
            val id = data.getLongExtra("ID", -1L)
            if (id != -1L) scrollToItem(id)
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
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
