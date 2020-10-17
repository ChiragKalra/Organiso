package com.bruhascended.organiso

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.analytics.AnalyticsLogger
import com.bruhascended.organiso.db.ContactsProvider
import com.bruhascended.organiso.data.SMSManager.Companion.ACTION_NEW_MESSAGE
import com.bruhascended.organiso.data.SMSManager.Companion.ACTION_OVERWRITE_MESSAGE
import com.bruhascended.organiso.data.SMSManager.Companion.ACTION_UPDATE_STATUS_MESSAGE
import com.bruhascended.organiso.data.SMSManager.Companion.EXTRA_MESSAGE
import com.bruhascended.organiso.data.SMSManager.Companion.EXTRA_MESSAGE_DATE
import com.bruhascended.organiso.data.SMSManager.Companion.EXTRA_MESSAGE_TYPE
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.ui.common.ListSelectionManager
import com.bruhascended.organiso.ui.common.ListSelectionManager.SelectionRecyclerAdaptor
import com.bruhascended.organiso.db.MainDaoProvider
import com.bruhascended.organiso.ui.common.MediaPreviewActivity
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.conversation.ConversationMenuOptions
import com.bruhascended.organiso.ui.conversation.ConversationViewModel
import com.bruhascended.organiso.ui.conversation.MessageRecyclerAdaptor
import com.bruhascended.organiso.ui.conversation.MessageSelectionListener
import com.bruhascended.organiso.ui.settings.GeneralFragment.Companion.PREF_DARK_THEME
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

@Suppress("UNCHECKED_CAST")
class ConversationActivity : MediaPreviewActivity() {

    companion object {
        const val EXTRA_SENDER = "SENDER"
        const val EXTRA_CONVERSATION = "CONVERSATION"
        const val EXTRA_MESSAGE_ID = "MESSAGE_ID"

        var activeConversationSender: String? = null
    }

    private lateinit var mContext: Context
    private lateinit var mViewModel: ConversationViewModel
    private lateinit var mAdaptor: MessageRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var analyticsLogger: AnalyticsLogger
    private lateinit var selectionManager: ListSelectionManager<Message>
    private lateinit var conversationMenuOptions: ConversationMenuOptions
    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private var inputManager: InputMethodManager? = null

    private var scroll = true

    override lateinit var mVideoView: VideoView
    override lateinit var mImagePreview: ImageView
    override lateinit var mSeekBar: SeekBar
    override lateinit var mPlayPauseButton: ImageButton
    override lateinit var mVideoPlayPauseButton: ImageButton
    override lateinit var mAddMedia: ImageButton


    private val messageUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context, intent: Intent) {
            when (intent.action) {
                ACTION_NEW_MESSAGE -> {
                    val message = intent.getSerializableExtra(EXTRA_MESSAGE) as Message
                    mViewModel.dao.insert(message)
                }
                ACTION_OVERWRITE_MESSAGE -> {
                    val message = intent.getSerializableExtra(EXTRA_MESSAGE) as Message
                    val qs = mViewModel.dao.search(message.time)
                    for (m in qs) {
                        message.id = m.id
                        mViewModel.dao.delete(m)
                    }
                    if (message.id != null) mViewModel.dao.delete(message)
                    mViewModel.dao.insert(message)
                }
                ACTION_UPDATE_STATUS_MESSAGE -> {
                    val date = intent.getLongExtra(EXTRA_MESSAGE_DATE, 0)
                    val type = intent.getIntExtra(EXTRA_MESSAGE_TYPE, 0)
                    val qs = mViewModel.dao.search(date).first()
                    qs.type = type
                    mViewModel.dao.update(qs)
                }
            }
        }
    }

    private fun toggleGoToBottomButtonVisibility(){
        val dp = resources.displayMetrics.density
        if (mLayoutManager.findFirstVisibleItemPosition() > 5 && !mViewModel.goToBottomVisible) {
            mViewModel.goToBottomVisible = true
            goToBottom.animate().alpha(1f).translationY(0f)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        } else if (mLayoutManager.findFirstVisibleItemPosition() <= 5 && mViewModel.goToBottomVisible) {
            mViewModel.goToBottomVisible = false
            goToBottom.animate().alpha(0f).translationY(48*dp)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    private fun trackLastMessage() {
        mViewModel.loadLast().observe(this, {
            mViewModel.apply {
                if (it != null) {
                    if (conversation.lastSMS != it.text ||
                        conversation.time != it.time ||
                        conversation.lastMMS != (it.path != null)
                    ) {
                        conversation.lastSMS = it.text
                        conversation.time = it.time
                        conversation.lastMMS = it.path != null
                        MainDaoProvider(mContext).getMainDaos()[conversation.label].update(conversation)
                    }
                } else {
                    MainDaoProvider(mContext).getMainDaos()[conversation.label].delete(conversation)
                }
            }
        })
    }

    private val scrollToItemAfterSearch = registerForActivityResult(StartActivityForResult()) {
        it.apply {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val id = data!!.getLongExtra("ID", -1L)
                if (id != -1L) scrollToItem(id)
            }
        }
    }

    private fun scrollToItem(id: Long) {
        val index = mViewModel.messages.indexOfFirst { m -> m.id==id }
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
        mViewModel.loadAll().observe(this, {
            mViewModel.messages = it
            if (scroll) {
                scroll = false
                val id = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
                if (id != -1L) {
                    recyclerView.postDelayed({
                        scrollToItem(id)
                    }, 200)
                }
            }
        })

        mAdaptor = MessageRecyclerAdaptor(mContext, smsSender, mmsSender)
        val mListener =  MessageSelectionListener(mContext, mViewModel.dao)
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
            addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        toggleGoToBottomButtonVisibility()
                    }
                }
            )
        }

        lifecycleScope.launch {
            mViewModel.pagingFlow.cachedIn(lifecycleScope).collectLatest {
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
        super.onCreate(savedInstanceState)

        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            PREF_DARK_THEME,
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_conversation)
        setSupportActionBar(toolbar)

        val temp by viewModels<ConversationViewModel>()
        mViewModel = temp
        mViewModel.init(
            intent.getSerializableExtra(EXTRA_CONVERSATION) as Conversation?
                ?: Conversation(intent.getStringExtra(EXTRA_SENDER)!!)
        )

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        if (mViewModel.conversation.id == null) {
            Thread {
                var found = false
                for (i in 0..4) {
                    val res = MainDaoProvider(mContext).getMainDaos()[i].findBySender(mViewModel.conversation.sender)
                    if (res.isNotEmpty()) {
                        mViewModel.conversation = res.first()
                        found = true
                        break
                    }
                }
                if (!found) {
                    mViewModel.name = ContactsProvider(this).getNameOrNull(mViewModel.sender)
                }
                toolbar.post {
                    supportActionBar!!.title = mViewModel.name ?: mViewModel.sender
                }
                if (mViewModel.conversation.id != null) {
                    if (!mViewModel.conversation.read) {
                        mViewModel.conversation.read = true
                        MainDaoProvider(mContext).getMainDaos()[mViewModel.conversation.label].update(mViewModel.conversation)
                    }
                }
            }.start()
        } else if (!mViewModel.conversation.read) {
            mViewModel.conversation.read = true
            MainDaoProvider(mContext).getMainDaos()[mViewModel.conversation.label].update(mViewModel.conversation)
        }


        smsSender = SMSSender(this, arrayOf(mViewModel.conversation))
        mmsSender = MMSSender(this, arrayOf(mViewModel.conversation))
        analyticsLogger = AnalyticsLogger(this)
        activeConversationSender = mViewModel.conversation.sender

        conversationMenuOptions = ConversationMenuOptions(
            this, mViewModel.conversation, analyticsLogger, scrollToItemAfterSearch
        )

        supportActionBar!!.title = mViewModel.conversation.name ?: mViewModel.conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        mVideoView = videoView
        mImagePreview = imagePreview
        mSeekBar = seekBar
        mPlayPauseButton = playPauseButton
        mVideoPlayPauseButton = videoPlayPauseButton
        mAddMedia = addMedia

        if (!mViewModel.conversation.sender.first().isDigit()) {
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

        setupRecycler()
        goToBottom.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
        toggleGoToBottomButtonVisibility()
        trackLastMessage()
    }

    override fun onOptionsItemSelected(item: MenuItem)
            = conversationMenuOptions.onOptionsItemSelected(item)

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val muteItem = menu.findItem(R.id.action_mute)
        val callItem = menu.findItem(R.id.action_call)
        val contactItem = menu.findItem(R.id.action_contact)

        mViewModel.conversation.apply {
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

    override fun onStart() {
        if (mViewModel.conversation.id != null)
            NotificationManagerCompat.from(this).cancel(mViewModel.conversation.id!!.toInt())
        activeConversationSender = mViewModel.conversation.sender
        registerReceiver(messageUpdatedReceiver, IntentFilter().apply {
            addAction(ACTION_OVERWRITE_MESSAGE)
            addAction(ACTION_UPDATE_STATUS_MESSAGE)
            addAction(ACTION_NEW_MESSAGE)
        })
        super.onStart()
    }

    override fun onStop() {
        activeConversationSender = null
        unregisterReceiver(messageUpdatedReceiver)
        super.onStop()
    }
}
