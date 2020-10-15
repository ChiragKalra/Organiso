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
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.organiso.data.ContactsManager
import com.bruhascended.organiso.data.ContactsManager.Contact
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.ui.common.MediaPreviewActivity
import com.bruhascended.organiso.ui.newConversation.AddressRecyclerAdaptor
import com.bruhascended.organiso.ui.newConversation.ContactRecyclerAdaptor
import kotlinx.android.synthetic.main.activity_new_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

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
    private lateinit var contacts: Array<Contact>
    private val adds = arrayListOf<Contact>()
    private lateinit var cm: ContactsManager
    private lateinit var addressRecyclerAdaptor: AddressRecyclerAdaptor

    override lateinit var mVideoView: VideoView
    override lateinit var mImagePreview: ImageView
    override lateinit var mSeekBar: SeekBar
    override lateinit var mPlayPauseButton: ImageButton
    override lateinit var mVideoPlayPauseButton: ImageButton
    override lateinit var mAddMedia: ImageButton

    private val clickAction = { contact: Contact ->
        to.text = null
        if (adds.firstOrNull{ it.number == contact.number } == null) {
            adds.add(contact)
            addressRecyclerAdaptor.notifyItemInserted(adds.lastIndex)
        }
    }

    private val addressClickAction = { contact: Contact ->
        to.setText(contact.number)
        to.setSelection(contact.number.length)
    }

    private fun getRecipients(uri: Uri): String {
        val base: String = uri.schemeSpecificPart
        val pos = base.indexOf('?')
        return if (pos == -1) base else base.substring(0, pos)
    }

    private fun processIntentData(intent: Intent) {
        if (Intent.ACTION_SENDTO == intent.action) {
            val destinations = TextUtils.split(getRecipients(intent.data!!), ";")
            destinations.forEach { address ->
                val clean = cm.getRaw(address)
                if (adds.firstOrNull{ it.number == clean } == null) {
                    adds.add(Contact("", clean))
                }
            }
        } else if (Intent.ACTION_SEND == intent.action && intent.type != null) {
            when {
                intent.type!!.startsWith("text") -> {
                    val str = intent.getStringExtra(Intent.EXTRA_TEXT)
                    messageEditText.setText(str)
                }
                intent.type != "multi" -> {
                    showMediaPreview(intent)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processMultiData(intent: Intent) {
        if (Intent.ACTION_SEND != intent.action || intent.type != "multi") return

        supportActionBar!!.title = "New Conversation"
        addMedia.isVisible = false

        val msgs = intent.getSerializableExtra("data") as Array<Message>
        messageEditText.apply {
            setText(getString(R.string.messages, msgs.size))
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isCursorVisible = false
            keyListener = null
        }
        sendButton.setOnClickListener {
            val conversations = Array(adds.size) {
                Conversation(adds[it].number)
            }
            val smsSender = SMSSender(mContext, conversations)
            val mmsSender = MMSSender(mContext, conversations)

            msgs.forEach {
                if (it.path == null) smsSender.sendSMS(it.text)
                else {
                    val uri =  Uri.fromFile(File(it.path!!))
                    mmsSender.sendMMS(it.text, uri, getMimeType(it.path!!))
                }
            }
            startActivityIfNeeded(
                Intent(mContext, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
            )
        }
    }

    private fun displaySearch(contacts: Array<Contact>) {
        val filtered = ArrayList<Contact>()
        val key = to.text.toString().trim().toLowerCase(Locale.ROOT)

        val adaptor = if (key.isNotEmpty()) {
            for (contact in contacts) {
                if ((Regex("\\b${key}").matches(contact.number) && key.first()
                        .isLetter()) or
                    (Regex("\\b${key}").matches(contact.name) && key.first()
                        .isDigit())
                ) {
                    filtered.add(contact)
                }
            }
            ContactRecyclerAdaptor(this, filtered.toTypedArray())
        } else ContactRecyclerAdaptor(this, contacts)

        adaptor.onItemClick = clickAction
        contactListView.adapter = adaptor
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        val dark = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dark_theme", false)
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_new_conversation)

        mContext = this
        mVideoView = videoView
        mImagePreview = imagePreview
        mSeekBar = seekBar
        mPlayPauseButton = playPauseButton
        mVideoPlayPauseButton = videoPlayPauseButton
        mAddMedia = addMedia

        cm = ContactsManager(this)

        processIntentData(intent)
        to.requestFocus()
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.title = "New Conversation"


        val observer = Observer<Array<Contact>?> { contacts ->
            if (contacts == null) return@Observer

            val adaptor = ContactRecyclerAdaptor(this, contacts)
            adaptor.onItemClick = clickAction

            contactListView.layoutManager = LinearLayoutManager(this).apply {
                orientation = LinearLayoutManager.HORIZONTAL
            }
            contactListView.adapter = adaptor
            progressBar.visibility = ProgressBar.INVISIBLE

            addsRecycler.layoutManager = LinearLayoutManager(this).apply {
                orientation = LinearLayoutManager.HORIZONTAL
            }
            addressRecyclerAdaptor = AddressRecyclerAdaptor(this, adds.apply {
                forEachIndexed { index, contact ->
                    val found = contacts.firstOrNull {it.number == contact.number}
                    if (found != null) adds[index].name = found.name
                }
            }).apply {
                onItemClick = addressClickAction
            }
            addsRecycler.adapter = addressRecyclerAdaptor

            to.doOnTextChanged { _, _, _, _ -> displaySearch(contacts) }
            if (to.text.isNotBlank()) displaySearch(contacts)

            sendButton.setOnClickListener {
                if (messageEditText.text.toString().trim() == "" && mmsType == 0)
                    return@setOnClickListener

                val sender: String = to.text.toString().trim()
                if (adds.isNotEmpty()) {
                    to.onEditorAction(EditorInfo.IME_ACTION_DONE)
                    val conversations = Array(adds.size) {
                        Conversation(adds[it].number)
                    }

                    val msg = messageEditText.text.toString().trim()
                    if (mmsType == 0) SMSSender(mContext, conversations).sendSMS(msg)
                    else {
                        MMSSender(mContext, conversations)
                            .sendMMS(msg, mmsURI, mmsTypeString)
                    }

                    startActivityIfNeeded(
                        Intent(mContext, MainActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
                    )

                    messageEditText.text = null
                } else if (sender.isNotBlank()) {
                    var name: String? = null

                    for (contact in contacts) {
                        if (contact.number == sender) {
                            name = contact.name
                            break
                        }
                    }
                    val intent = Intent(this, ConversationActivity::class.java)
                    intent.putExtra("ye",
                        Conversation(
                            sender, name,
                            lastSMS = messageEditText.text.toString().trim()
                        )
                    )
                    if (mmsType > 0) intent.data = mmsURI
                    startActivity(intent)
                    this.finish()
                }
            }
            processMultiData(intent)
            to.setOnEditorActionListener { _, i, _ ->
                if (i != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener true
                val key = to.text.toString().trim().toLowerCase(Locale.ROOT)
                    .filter { it.isDigit() }
                if (key.isBlank()) return@setOnEditorActionListener true
                val found = contacts.firstOrNull {it.number == key}
                clickAction(Contact(found?.name ?: "", key))
                true
            }
        }
        Thread {
            if (isMainViewModelNull()) {
                requireMainViewModel(this)
                mainViewModel.contacts.postValue(ContactsManager(this).getContactsList())
            }
            runOnUiThread{
                mainViewModel.contacts.observe(this, observer)
            }
        }.start()

        super.onCreate(savedInstanceState)
    }

}
