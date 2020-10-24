package com.bruhascended.organiso

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.data.ContactsManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.*
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.common.MediaPreviewActivity
import com.bruhascended.core.constants.saveFile
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import com.bruhascended.organiso.ui.newConversation.RecipientRecyclerAdaptor
import com.bruhascended.organiso.ui.newConversation.ContactRecyclerAdaptor
import kotlinx.android.synthetic.main.activity_new_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

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

class NewConversationActivity : MediaPreviewActivity() {

    private lateinit var mContext: Context
    private lateinit var cm: ContactsManager
    private lateinit var addressRecyclerAdaptor: RecipientRecyclerAdaptor
    private lateinit var mAdaptor: ContactRecyclerAdaptor
    private lateinit var mContactsProvider: ContactsProvider
    private lateinit var mSavedDao: SavedDao

    private val recipients = arrayListOf<Contact>()

    override lateinit var mVideoView: VideoView
    override lateinit var mImagePreview: ImageView
    override lateinit var mSeekBar: SeekBar
    override lateinit var mPlayPauseButton: ImageButton
    override lateinit var mVideoPlayPauseButton: ImageButton
    override lateinit var mAddMedia: ImageButton

    private val clickAction = { contact: Contact ->
        if (!to.text.isNullOrBlank()) to.text = null
        addRecipient(contact)
    }

    private val addressClickAction = { contact: Contact ->
        to.setText(contact.address)
        to.setSelection(contact.address.length)
        removeClickAction(contact)
    }

    private val removeClickAction = { contact: Contact ->
        val ind = recipients.indexOf(contact)
        recipients.removeAt(ind)
        addressRecyclerAdaptor.notifyItemRemoved(ind)
    }

    private fun getRecipients(uri: Uri): String {
        val base: String = uri.schemeSpecificPart
        val pos = base.indexOf('?')
        return if (pos == -1) base else base.substring(0, pos)
    }

    private fun addRecipientAsync (number: String) {
        Thread {
            val clean = cm.getClean (
                number.trim().filter { it.isDigit() }
            )
            if (clean.isBlank()) return@Thread
            val name = mContactsProvider.getNameOrNull(clean) ?: ""
            if (recipients.firstOrNull { it.clean == clean } == null) {
                addsRecycler.post {
                    recipients.add(Contact(name, clean, number, 0))
                    addressRecyclerAdaptor.notifyItemInserted(recipients.lastIndex)
                }
            }
        }.start()
    }

    private fun addRecipientSync (number: String) {
        val clean = cm.getClean (
            number.trim().toLowerCase(Locale.ROOT).filter { it.isDigit() }
        )
        if (clean.isBlank()) return
        val name = mContactsProvider.getNameOrNull(clean) ?: ""
        if (recipients.firstOrNull { it.clean == clean } == null) {
            recipients.add(Contact(name, clean, number, 0))
        }
    }

    private fun addRecipient (contact: Contact) {
        if (recipients.firstOrNull { it.clean == contact.clean } == null) {
            recipients.add(contact)
            addressRecyclerAdaptor.notifyItemInserted(recipients.lastIndex)
        }
    }

    private fun startNextActivity () {
        if (recipients.size == 1) {
            startActivity(
                Intent(this, ConversationActivity::class.java)
                    .putExtra(EXTRA_CONVERSATION, Conversation(
                        recipients.first().address,
                        recipients.first().clean,
                        name = recipients.first().name
                    ))
            )
        } else if (recipients.isNotEmpty()) {
            startActivityIfNeeded(
                Intent(mContext, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
            )
        }
        to.text = null
        messageEditText.text = null
        recipients.clear()
        addressRecyclerAdaptor.notifyDataSetChanged()
    }

    @Suppress("UNCHECKED_CAST")
    private fun processIntentData() {
        if (Intent.ACTION_SENDTO == intent.action) {
            val destinations = TextUtils.split(getRecipients(intent.data!!), ";")
            destinations.forEach { addRecipientAsync(it) }
        } else if (Intent.ACTION_SEND == intent.action && intent.type != null) {
            when {
                intent.type!!.startsWith("text") -> {
                    val str = intent.getStringExtra(Intent.EXTRA_TEXT)
                    messageEditText.setText(str)
                }
                intent.type == TYPE_MULTI -> {
                    addMedia.isVisible = false
                    extraButton.isVisible = false

                    val msgs = intent.getSerializableExtra(EXTRA_MESSAGES) as Array<Message>
                    messageEditText.apply {
                        setText(getString(R.string.messages, msgs.size))
                        setBackgroundColor(Color.TRANSPARENT)
                        isFocusable = false
                        isCursorVisible = false
                        keyListener = null
                    }
                    sendButton.setOnClickListener {
                        val conversations = Array(recipients.size) {
                            Conversation(recipients[it].address, recipients[it].clean)
                        }
                        val smsSender = SMSSender(mContext, conversations)
                        val mmsSender = MMSSender(mContext, conversations)

                        msgs.forEach {
                            if (it.path == null) smsSender.sendSMS(it.text)
                            else {
                                val uri =  Uri.fromFile(File(it.path!!))
                                mmsSender.sendMMS(it.text, uri)
                            }
                        }
                        startNextActivity()
                    }
                }
                else -> showMediaPreview(intent)
            }
        }
    }

    private fun search(key: String) {
        val flow = Pager(
            PagingConfig(
                pageSize = 1,
                initialLoadSize = 1,
                prefetchDistance = 24,
                maxSize = 120,
            )
        ) {
            mContactsProvider.getPaged(key)
        }.flow.cachedIn(lifecycleScope)
        lifecycleScope.launch {
            flow.collectLatest {
                mAdaptor.submitData(it)
            }
        }
        mAdaptor.notifyDataSetChanged()
    }

    private fun setupContactRecycler() {
        mAdaptor = ContactRecyclerAdaptor(this)
        mAdaptor.onItemClick = clickAction
        contactListView.layoutManager = LinearLayoutManager(this).apply {
            orientation = LinearLayoutManager.HORIZONTAL
        }
        contactListView.adapter = mAdaptor
        search("")
        clear_text.setOnClickListener {
            to.text = null
        }
        to.doOnTextChanged { text, _, _, _ ->
            clear_text.isVisible = text.toString().isNotBlank()
            search(text.toString().trim())
        }
    }

    private fun setupAddressRecycler() {
        addsRecycler.layoutManager = LinearLayoutManager(this).apply {
            orientation = LinearLayoutManager.HORIZONTAL
        }
        addressRecyclerAdaptor = RecipientRecyclerAdaptor(this, recipients).apply {
            onItemClick = addressClickAction
            onRemoveClick = removeClickAction
        }
        addsRecycler.adapter = addressRecyclerAdaptor

        to.requestFocus()
        to.setOnEditorActionListener { _, i, _ ->
            if (i != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener true
            val number = to.text.toString()
            if (number.isNotBlank()) addRecipientAsync(number)
            to.text = null
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPrefTheme()
        setContentView(R.layout.activity_new_conversation)
        setupToolbar(toolbar, getString(R.string.new_conversation))

        mContext = this
        mVideoView = videoView
        mImagePreview = imagePreview
        mSeekBar = seekBar
        mPlayPauseButton = playPauseButton
        mVideoPlayPauseButton = videoPlayPauseButton
        mAddMedia = addMedia
        cm = ContactsManager(this)
        mContactsProvider = ContactsProvider(this)
        mSavedDao = SavedDbFactory(mContext).get().manager()

        processIntentData()
        setupAddressRecycler()
        setupContactRecycler()

        to.text = null

        if (!sendButton.hasOnClickListeners()) {
            extraButton.setImageResource(R.drawable.ic_favorite)
            extraButton.setOnClickListener {
                val msg = messageEditText.text.toString().trim()
                if (msg.isEmpty() && !isMms)
                    return@setOnClickListener

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
            }
            sendButton.setOnClickListener {
                if (messageEditText.text.toString().trim() == "" && !isMms)
                    return@setOnClickListener

                addRecipientSync(to.text.toString())
                to.text = null

                if (recipients.isNotEmpty()) {
                    val conversations = Array(recipients.size) {
                        Conversation(recipients[it].address, recipients[it].clean)
                    }
                    val msg = messageEditText.text.toString().trim()
                    if (!isMms) {
                        SMSSender(mContext, conversations).sendSMS(msg)
                        hideMediaPreview()
                    } else {
                        MMSSender(mContext, conversations)
                            .sendMMS(msg, mmsURI!!)
                    }
                    messageEditText.text = null
                }
                startNextActivity()
            }
        }
    }
}
