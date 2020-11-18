package com.bruhascended.organiso

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.core.constants.*
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.data.DraftsManager
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.core.db.*
import com.bruhascended.core.model.toFloat
import com.bruhascended.organiso.common.*
import com.bruhascended.organiso.notifications.NotificationActionReceiver.Companion.cancelNotification
import com.bruhascended.organiso.services.ScheduledManager
import com.bruhascended.organiso.services.SenderService
import com.bruhascended.organiso.ui.conversation.ConversationMenuOptions
import com.bruhascended.organiso.ui.conversation.ConversationViewModel
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

@Suppress("UNCHECKED_CAST")
class ConversationActivity : MediaPreviewActivity() {

    companion object {
        var activeConversationNumber: String? = null
        var activeConversationDao: MessageDao? = null
    }

    private val mViewModel: ConversationViewModel by viewModels()


    private lateinit var mAdaptor: MessageRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var selectionManager: ListSelectionManager<Message>
    private lateinit var conversationMenuOptions: ConversationMenuOptions
    private lateinit var mDraftsManager: DraftsManager
    private lateinit var mScheduledSMSSender: ScheduledManager
    private lateinit var mSavedDao: SavedDao
    private var inputManager: InputMethodManager? = null

    private var scroll = true
    private val mContext = this
    private val mainDaoProvider = MainDaoProvider(this)

    override lateinit var mVideoView: VideoView
    override lateinit var mImagePreview: ImageView
    override lateinit var mSeekBar: SeekBar
    override lateinit var mPlayPauseButton: ImageButton
    override lateinit var mVideoPlayPauseButton: ImageButton
    override lateinit var mAddMedia: ImageButton

    private fun Int.toPx() = this * resources.displayMetrics.density

    private fun toggleGoToBottomButtonVisibility() {
        if (mLayoutManager.findFirstVisibleItemPosition() > 1 && !mViewModel.goToBottomVisible) {
            mViewModel.goToBottomVisible = true
            goToBottom.animate().alpha(1f).translationY(0f)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        } else if (mLayoutManager.findFirstVisibleItemPosition() <= 5 && mViewModel.goToBottomVisible) {
            mViewModel.goToBottomVisible = false
            goToBottom.animate().alpha(0f).translationY(48.toPx())
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    private fun sendMessage() {
        startService(
            Intent(this, SenderService::class.java).apply {
                putExtra(EXTRA_NUMBER, mViewModel.number)
                putExtra(EXTRA_MESSAGE_TEXT, messageEditText.text.toString())
                data = mmsURI
            }
        )
        messageEditText.text = null
        hideMediaPreview()
        toggleExtraVisibility(false)
    }

    private fun toggleExtraVisibility(vis: Boolean? = null) {
        if (vis == null) mViewModel.extraIsVisible = !mViewModel.extraIsVisible
        else mViewModel.extraIsVisible = vis
        val extras = arrayOf(
            favoriteButton,
            draftButton,
            timedButton
        )
        val visible = mViewModel.extraIsVisible
        extras.forEachIndexed { i, b ->
            b.animate()
                .alpha(visible.toFloat())
                .translationY(if (visible) 0f else 64.toPx() * (i + 1))
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
        extraButton.animate()
            .rotation(if (visible) 45f else 0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setDuration(300)
            .start()
    }

    private val scrollToItemAfterSearch = registerForActivityResult(StartActivityForResult()) {
        it.apply {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val id = data!!.getIntExtra(EXTRA_MESSAGE_ID, -1)
                if (id != -1) scrollToItem(id)
            }
        }
    }

    private fun scrollToItem(id: Int) {
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
                val id = intent?.getIntExtra(EXTRA_MESSAGE_ID, -1) ?: -1
                if (id != -1) {
                    recyclerView.postDelayed({
                        scrollToItem(id)
                    }, 200)
                }
            }
        })

        mAdaptor = MessageRecyclerAdaptor(mContext)
        val mListener =  MessageSelectionListener(mContext, mViewModel.dao, mViewModel.number)
        selectionManager = ListSelectionManager(
            mContext as AppCompatActivity,
            mAdaptor as MyPagingDataAdapter<Message, RecyclerView.ViewHolder>,
            mListener
        )
        mAdaptor.setOnRetry {
            startService(
                Intent(this, SenderService::class.java).apply {
                    putExtra(EXTRA_NUMBER, mViewModel.number)
                    putExtra(EXTRA_MESSAGE_ID, it.id)
                }
            )
        }
        mAdaptor.selectionManager = selectionManager
        mListener.selectionManager = selectionManager
        recyclerView.apply {
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

    private fun getConversation() {
        Thread {
            for (i in 0..4) {
                val res = mainDaoProvider.getMainDaos()[i].findByNumber(mViewModel.conversation.number)
                if (res != null) {
                    mViewModel.conversation = res
                    break
                }
            }
            sendLayout.post {
                doOnConversation()
            }
        }.start()
    }

    private fun markRead() {
        for (i in 0..4) {
            mainDaoProvider.getMainDaos()[i].markRead(mViewModel.number)
        }
    }

    private fun doOnConversation() {
        if (mViewModel.isBot || mViewModel.label == LABEL_BLOCKED) {
            sendLayout.visibility = View.INVISIBLE
            favoriteButton.visibility = View.GONE
            timedButton.visibility = View.GONE
            draftButton.visibility = View.GONE
            notSupported.visibility = View.VISIBLE
            notSupported.text = if (mViewModel.label == LABEL_BLOCKED)
                getString(R.string.number_blocked) else getString(R.string.sending_messages_not_supported)
        }
    }

    private fun liveUpdateTime() {
        mViewModel.dao.loadLastLive().observe(this) {
            if (it != null) {
                mainDaoProvider.getMainDaos()[mViewModel.label].updateTime(
                    mViewModel.number,
                    it.time
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_conversation)

        mVideoView = videoView
        mImagePreview = imagePreview
        mSeekBar = seekBar
        mPlayPauseButton = playPauseButton
        mVideoPlayPauseButton = videoPlayPauseButton
        mAddMedia = addMedia

        val receivedConversation = when {
            intent.extras!!.containsKey(EXTRA_CONVERSATION) -> {
                intent.getSerializableExtra(EXTRA_CONVERSATION) as Conversation
            }
            intent.extras!!.containsKey(EXTRA_CONVERSATION_JSON) -> {
                intent.getStringExtra(EXTRA_CONVERSATION_JSON).toConversation()
            }
            else -> {
                Conversation(intent.getStringExtra(EXTRA_NUMBER)!!)
            }
        }
        mViewModel.init(receivedConversation)
        setupToolbar(
            toolbar,
            ContactsProvider(this).getNameOrNull(mViewModel.number)
                ?: mViewModel.number
        )
        markRead()
        getConversation()

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        mDraftsManager = DraftsManager(this, mViewModel.dao)
        mScheduledSMSSender = ScheduledManager(this, mViewModel.dao)
        mSavedDao = SavedDbFactory(this).get().manager()

        conversationMenuOptions = ConversationMenuOptions(
            this, mViewModel.conversation, scrollToItemAfterSearch
        )

        setupRecycler()
        liveUpdateTime()
        goToBottom.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }
        toggleGoToBottomButtonVisibility()


        var sending = false
        val onSend = {
            if (!sending) {
                sending = true
                if (isMms || messageEditText.text.toString().trim() != "") {
                    sendMessage()
                }
                sending = false
            }
        }

        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_ENTER_SEND, false)
        ) {
            messageEditText.setSingleLine()
            messageEditText.imeOptions = EditorInfo.IME_ACTION_SEND
            messageEditText.setOnEditorActionListener { _, i, _ ->
                if (i != EditorInfo.IME_ACTION_SEND) return@setOnEditorActionListener true
                onSend()
                true
            }
        }

        sendButton.setOnClickListener {
            onSend()
        }

        toggleExtraVisibility(mViewModel.extraIsVisible)
        extraButton.setOnClickListener {
            toggleExtraVisibility()
        }
        favoriteButton.setOnClickListener {
            val msg = messageEditText.text.toString().trim()
            if (msg.isEmpty() && !isMms) {
                Toast.makeText(this, "Message is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            mSavedDao.insert(
                Saved(
                    msg,
                    System.currentTimeMillis(),
                    SAVED_TYPE_DRAFT,
                    path = saveFile(
                        mmsURI,
                        System.currentTimeMillis().toString()
                    )
                )
            )
            Toast.makeText(
                this, getString(R.string.added_to_favorites), Toast.LENGTH_LONG
            ).show()
            toggleExtraVisibility(false)
        }

        draftButton.setOnClickListener{
            val msg = messageEditText.text.toString().trim()
            if (msg.isEmpty() && !isMms) {
                Toast.makeText(this, "Message is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            messageEditText.text = null
            mDraftsManager.create(msg, mViewModel.number, mmsURI)
            toggleExtraVisibility(false)
        }
        mAdaptor.setOnDraftClick {
            mDraftsManager.delete(it)
            messageEditText.apply {
                setText(it.text)
                setSelection(it.text.length)
                requestFocus()
                inputManager?.showSoftInput(this, InputMethodManager.SHOW_FORCED)
            }
            mViewModel.extraIsVisible = false
            toggleExtraVisibility(false)
        }

        timedButton.setOnClickListener{
            val timeRn = System.currentTimeMillis()
            val calenderRn = Calendar.getInstance().apply {
                timeInMillis = timeRn
            }
            val editText = messageEditText
            val msg = editText.text.toString().trim()
            if (msg.isEmpty() && !isMms) {
                Toast.makeText(this, "Message is empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val calendar: Calendar = Calendar.getInstance()
            DatePickerDialog(this).apply {
                datePicker.minDate = System.currentTimeMillis()
                setOnDateSetListener { _, i, i2, i3 ->
                    calendar.set(i, i2, i3)

                    TimePickerDialog(
                        mContext,
                        { _, hr, min ->
                            calendar.set(Calendar.HOUR_OF_DAY, hr)
                            calendar.set(Calendar.MINUTE, min)
                            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                                Toast.makeText(
                                    mContext,
                                    getString(R.string.cant_send_scheduled_to_past),
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                editText.text = null
                                mScheduledSMSSender.add(
                                    calendar.timeInMillis,
                                    mViewModel.number,
                                    msg, mmsURI
                                )
                                Toast.makeText(
                                    mContext,
                                    getString(R.string.scheduled),
                                    Toast.LENGTH_LONG
                                ).show()
                                toggleExtraVisibility(false)
                            }
                        },
                        if (DateUtils.isToday(timeRn)) calenderRn[Calendar.HOUR_OF_DAY] else 0,
                        if (DateUtils.isToday(timeRn)) calenderRn[Calendar.MINUTE] + 1 else 0,
                        false
                    ).apply {
                        setTitle(R.string.send_scheduled)
                        setButton(
                            TimePickerDialog.BUTTON_POSITIVE,
                            getString(R.string.schedule)
                        ) { _, _ -> }
                    }.show()
                }
            }.show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        toggleExtraVisibility(false)
        return conversationMenuOptions.onOptionsItemSelected(item)
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        toggleExtraVisibility(false)
        super.onSupportActionModeStarted(mode)
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val muteItem = menu.findItem(R.id.action_mute)
        val callItem = menu.findItem(R.id.action_call)
        val contactItem = menu.findItem(R.id.action_contact)
        val blockItem = menu.findItem(R.id.action_block)
        val reportItem = menu.findItem(R.id.action_report_spam)

        if (mViewModel.label == LABEL_BLOCKED && blockItem != null) {
            menu.removeItem(blockItem.itemId)
        } else if (mViewModel.label == LABEL_SPAM && reportItem != null) {
            menu.removeItem(reportItem.itemId)
        }

        mViewModel.apply {
            muteItem.title = if (isMuted) getString(R.string.unMute) else getString(R.string.mute)
            callItem.isVisible = !conversation.isBot
            contactItem.isVisible = !conversation.isBot
            if (name != null) contactItem.title = getString(R.string.view_contact)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onStart() {
        cancelNotification(mViewModel.number, mViewModel.conversation.id)
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (id != -1) {
            NotificationManagerCompat.from(this).cancel(id)
        }

        activeConversationNumber = mViewModel.number
        activeConversationDao = mViewModel.dao
        super.onStart()
    }

    override fun onStop() {
        activeConversationNumber = null
        activeConversationDao = null
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        inputManager?.hideSoftInputFromWindow(
            messageEditText.windowToken,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )
        val msg = messageEditText.text.toString().trim()
        if (msg.isEmpty()) {
            return
        }
        mViewModel.apply {
            mDraftsManager.create(msg, mViewModel.number, mmsURI)
        }
        mainDaoProvider.getMainDaos()[mViewModel.label].updateTime(
            mViewModel.number,
            System.currentTimeMillis()
        )
    }
}
