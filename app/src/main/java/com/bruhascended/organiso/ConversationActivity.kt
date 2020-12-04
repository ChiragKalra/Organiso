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
            if (it != null && mViewModel.conversation.isInDb) {
                mainDaoProvider.getMainDaos()[mViewModel.label].updateTime(
                    mViewModel.number,
                    it.time
                )
            } else if (mViewModel.conversation.isInDb) {
                mainDaoProvider.getMainDaos()[mViewModel.label].delete(mViewModel.conversation)
            }
        }
    }

    private fun requestScheduled() {
        val timeRn = System.currentTimeMillis()
        val calenderRn = Calendar.getInstance().apply {
            timeInMillis = timeRn
        }
        val editText = messageEditText
        val msg = editText.text.toString().trim()

        // return if text field is empty and no media is added
        if (msg.isEmpty() && !isMms) {
            Toast.makeText(this, getString(R.string.empty_message), Toast.LENGTH_SHORT).show()
            return
        }

        // get date and time from user
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

                            // hide extra action buttons
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
        mDraftsManager = DraftsManager(this, mViewModel.dao, mViewModel.conversation)
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

        // add to favorite messages on click
        favoriteButton.setOnClickListener {
            val msg = messageEditText.text.toString().trim()

            // return if text field is empty and no media is added
            if (msg.isEmpty() && !isMms) {
                Toast.makeText(this, getString(R.string.empty_message), Toast.LENGTH_SHORT).show()
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

            // hide extra action buttons
            toggleExtraVisibility(false)
        }

        // save as draft on click
        draftButton.setOnClickListener{
            val msg = messageEditText.text.toString().trim()
            // return if text field is empty and no media is added
            if (msg.isEmpty() && !isMms) {
                Toast.makeText(this, getString(R.string.empty_message), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            messageEditText.text = null
            mDraftsManager.create(msg, mViewModel.number, mmsURI)

            // hide extra action buttons
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

            // hide extra action buttons
            toggleExtraVisibility(false)
        }

        // init scheduled message on click
        timedButton.setOnClickListener{
            requestScheduled()
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

    private fun Menu.removeItem(id: Int?) {
        if (id != null) {
            removeItem(id)
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // init menu items
        val muteItem = menu.findItem(R.id.action_mute) ?: null
        val callItem = menu.findItem(R.id.action_call) ?: null
        val contactItem = menu.findItem(R.id.action_contact) ?: null
        val blockItem = menu.findItem(R.id.action_block) ?: null
        val reportItem = menu.findItem(R.id.action_report_spam) ?: null
        val moveItem = menu.findItem(R.id.action_move) ?: null
        val deleteItem = menu.findItem(R.id.action_delete) ?: null

        // remove menu items for different contexts
        if (mViewModel.label == LABEL_BLOCKED) {
            menu.removeItem(blockItem?.itemId)
        } else if (mViewModel.label == LABEL_SPAM) {
            menu.removeItem(reportItem?.itemId)
        } else if (!mViewModel.conversation.isInDb) {
            menu.removeItem(muteItem?.itemId)
            menu.removeItem(moveItem?.itemId)
            menu.removeItem(reportItem?.itemId)
            menu.removeItem(blockItem?.itemId)
            menu.removeItem(deleteItem?.itemId)
        }

        mViewModel.apply {
            muteItem?.title = if (isMuted) getString(R.string.unMute) else getString(R.string.mute)
            callItem?.isVisible = !conversation.isBot
            contactItem?.isVisible = !conversation.isBot
            if (name != null) contactItem?.title = getString(R.string.view_contact)
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
        // force hide keyboard
        inputManager?.hideSoftInputFromWindow(
            messageEditText.windowToken,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )

        // save as draft if text field isn't empty
        val msg = messageEditText.text.toString().trim()
        if (msg.isNotBlank()) {
            mViewModel.apply {
                mDraftsManager.create(msg, mViewModel.number, mmsURI)
            }
        }
    }
}
