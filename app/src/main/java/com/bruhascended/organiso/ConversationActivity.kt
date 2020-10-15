package com.bruhascended.organiso

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.bruhascended.organiso.analytics.AnalyticsLogger
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.db.MessageDao
import com.bruhascended.organiso.db.MessageDatabase
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.ui.common.ListSelectionManager
import com.bruhascended.organiso.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.organiso.ui.common.MediaPreviewActivity
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.conversation.ConversationMenuOptions
import com.bruhascended.organiso.ui.conversation.MessageRecyclerAdaptor
import com.bruhascended.organiso.ui.conversation.MessageSelectionListener
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
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


const val selectMediaArg = 0
const val selectMessageArg = 1

@Suppress("UNCHECKED_CAST")
class ConversationActivity : MediaPreviewActivity() {

    private lateinit var mdb: MessageDao
    private lateinit var messages: List<Message>
    private lateinit var mContext: Context
    private lateinit var conversation: Conversation
    private lateinit var mAdaptor: MessageRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var analyticsLogger: AnalyticsLogger
    private lateinit var selectionManager: ListSelectionManager<Message>
    private lateinit var conversationMenuOptions: ConversationMenuOptions
    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender

    private var inputManager: InputMethodManager? = null
    private var goToBottomVisible = false
    private var scroll = true

    override lateinit var mVideoView: VideoView
    override lateinit var mImagePreview: ImageView
    override lateinit var mSeekBar: SeekBar
    override lateinit var mPlayPauseButton: ImageButton
    override lateinit var mVideoPlayPauseButton: ImageButton
    override lateinit var mAddMedia: ImageButton

    private val goToBottomScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val dp = resources.displayMetrics.density
            if (mLayoutManager.findFirstVisibleItemPosition() > 5 && !goToBottomVisible) {
                goToBottomVisible = true
                goToBottom.animate().alpha(1f).translationY(0f)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else if (mLayoutManager.findFirstVisibleItemPosition() <= 5 && goToBottomVisible) {
                goToBottomVisible = false
                goToBottom.animate().alpha(0f).translationY(48*dp)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            }
        }
    }

    private fun trackLastMessage() {
        mdb.loadLast().observe(this, {
            if (it != null) {
                if (conversation.lastSMS != it.text ||
                    conversation.time != it.time ||
                    conversation.lastMMS != (it.path != null)
                ) {
                    conversation.lastSMS = it.text
                    conversation.time = it.time
                    conversation.lastMMS = it.path != null
                    mainViewModel.daos[conversation.label].update(conversation)
                }
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

    private fun setupRecycler() {
        activeConversationDao.loadAll().observe(this, {
            messages = it
            if (scroll) {
                scroll = false
                val id = intent?.getLongExtra("ID", -1L) ?: -1L
                if (id != -1L) {
                    recyclerView.postDelayed({
                        scrollToItem(id)
                    }, 200)
                }
            }
        })

        val flow = Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 60,
                maxSize = 180,
            )
        ) {
            mdb.loadAllPaged()
        }.flow.cachedIn(lifecycleScope)

        mAdaptor = MessageRecyclerAdaptor(mContext, smsSender, mmsSender)
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
            addOnScrollListener(goToBottomScrollListener)
            goToBottom.setOnClickListener {
                smoothScrollToPosition(0)
            }
        }

        lifecycleScope.launch {
            flow.collectLatest {
                mAdaptor.submitData(it)
            }
        }

        mAdaptor.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
                    recyclerView.scrollToPosition(0)
                }
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
                    recyclerView.scrollToPosition(0)
                } else {
                    recyclerView.smoothScrollToPosition(0)
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_conversation)
        setSupportActionBar(toolbar)
        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        conversation = intent.getSerializableExtra("ye") as Conversation? ?:
                Conversation(intent.getStringExtra("sender")!!, intent.getStringExtra("name"))

        requireMainViewModel(this)
        if (conversation.lastSMS.isBlank()) {
            for (i in 0..4) {
                val res = mainViewModel.daos[i].findBySender(conversation.sender)
                if (res.isNotEmpty()) {
                    conversation = res.first()
                    break
                }
            }
        }

        smsSender = SMSSender(this, arrayOf(conversation))
        mmsSender = MMSSender(this, arrayOf(conversation))
        analyticsLogger = AnalyticsLogger(this)
        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        activeConversationDao = mdb
        activeConversationSender = conversation.sender

        conversationMenuOptions = ConversationMenuOptions(this, conversation, analyticsLogger)

        mVideoView = videoView
        mImagePreview = imagePreview
        mSeekBar = seekBar
        mPlayPauseButton = playPauseButton
        mVideoPlayPauseButton = videoPlayPauseButton
        mAddMedia = addMedia

        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
        }

        sendButton.setOnClickListener {
            if (mmsType > 0) {
                sendButton.isEnabled = false
                mmsSender.sendMMS(messageEditText.text.toString(), mmsURI, mmsTypeString)
                sendButton.isEnabled = true
                messageEditText.setText("")
                hideMediaPreview()
            } else if (messageEditText.text.toString().trim() != "") {
                sendButton.isEnabled = false
                smsSender.sendSMS(messageEditText.text.toString())
                sendButton.isEnabled = true
                messageEditText.setText("")
            }
        }

        if (conversation.id != null) {
            if (!conversation.read) {
                conversation.read = true
                mainViewModel.daos[conversation.label].update(conversation)
            }
        } else if (!conversation.lastSMS.isBlank()) {
            messageEditText.setText(conversation.lastSMS)
            if (intent.data != null) showMediaPreview(intent)
            sendButton.callOnClick()
        } else if (intent.data != null) {
            showMediaPreview(intent)
            sendButton.callOnClick()
        }

        setupRecycler()
        trackLastMessage()
        super.onCreate(savedInstanceState)
    }

    override fun onOptionsItemSelected(item: MenuItem)
            = conversationMenuOptions.onOptionsItemSelected(item)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == selectMessageArg && resultCode == RESULT_OK && data != null) {
            val id = data.getLongExtra("ID", -1L)
            if (id != -1L) scrollToItem(id)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val muteItem = menu.findItem(R.id.action_mute)
        val callItem = menu.findItem(R.id.action_call)
        val contactItem = menu.findItem(R.id.action_contact)

        conversation.apply {
            muteItem.title = if (isMuted) getString(R.string.unMute) else getString(R.string.mute)
            callItem.isVisible = sender.first().isDigit()
            contactItem.isVisible = sender.first().isDigit()
            if (name != null) contactItem.title = getString(R.string.view_contact)
        }
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
        if (conversation.id != null)
            NotificationManagerCompat.from(this).cancel(conversation.id!!.toInt())
        activeConversationSender = conversation.sender
        super.onResume()
    }
}
