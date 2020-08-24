package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.newConversation.ContactListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import kotlinx.android.synthetic.main.activity_new_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import java.util.*
import kotlin.collections.ArrayList

class NewConversationActivity : AppCompatActivity() {

    private lateinit var mContext: Context

    private fun getRecipients(uri: Uri): String {
        val base: String = uri.schemeSpecificPart
        val pos = base.indexOf('?')
        return if (pos == -1) base else base.substring(0, pos)
    }

    private fun processIntentData(intent: Intent) {
        if (Intent.ACTION_SENDTO == intent.action) {
            val destinations = TextUtils.split(getRecipients(intent.data!!), ";")
            to.setText(ContactsManager(mContext).getRaw(destinations.first()))
            messageEditText.requestFocus()
        } else if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val str = intent.getStringExtra(Intent.EXTRA_TEXT)
            messageEditText.setText(str)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dark_theme", false).apply {
                if (this) setTheme(R.style.DarkTheme)
            }
        setContentView(R.layout.activity_new_conversation)

        val llm = LinearLayoutManager(this)
        val clickAction = { contact: Contact ->
            to.setText(contact.number)
            to.setSelection(contact.number.length)
        }

        mContext = this

        processIntentData(intent)
        to.requestFocus()
        supportActionBar!!.title = "New Conversation"
        llm.orientation = LinearLayoutManager.HORIZONTAL

        val observer = Observer<Array<Contact>?> {
            if (it != null) {
                val contacts = it

                var adaptor = ContactListViewAdaptor(contacts, this)
                adaptor.onItemClick = clickAction

                contactListView.layoutManager = llm
                contactListView.adapter = adaptor
                contactListView.visibility = RecyclerView.VISIBLE
                progressBar.visibility = ProgressBar.INVISIBLE

                fun displaySearch() {
                    val filtered = ArrayList<Contact>()
                    val key = to.text.toString().trim().toLowerCase(Locale.ROOT)

                    adaptor = if (key.isNotEmpty()) {
                        for (contact in contacts) {
                            if ((key in contact.name.toLowerCase(Locale.ROOT) && key.first()
                                    .isLetter()) or
                                (key in contact.number.toLowerCase(Locale.ROOT) && key.first()
                                    .isDigit())
                            ) {
                                filtered.add(contact)
                            }
                        }
                        ContactListViewAdaptor(filtered.toTypedArray(), this)
                    } else ContactListViewAdaptor(contacts, this)

                    adaptor.onItemClick = clickAction
                    contactListView.adapter = adaptor
                }

                to.doOnTextChanged { _, _, _, _ -> displaySearch() }
                if (to.text.isNotBlank()) displaySearch()

                sendButton.setOnClickListener {
                    if (messageEditText.text.toString().trim() != "") {
                        var name: String? = null
                        val sender: String = to.text.toString().trim()

                        for (contact in contacts) {
                            if (contact.number == sender) {
                                name = contact.name
                                break
                            }
                        }
                        val intent = Intent(this, ConversationActivity::class.java)
                        intent.putExtra("ye",
                            Conversation(
                                null,
                                sender,
                                name,
                                "",
                                true,
                                0,
                                messageEditText.text.toString().trim(),
                                0,
                                -1,
                                FloatArray(5) { its ->
                                    if (its == 0) 1f else 0f
                                }
                            )
                        )
                        startActivity(intent)
                        this.finish()
                    }
                }
            }
        }
        Thread {
            if (isMainViewModelNull()) {
                mainViewModel = MainViewModel()
                mainViewModel.contacts.postValue(ContactsManager(this).getContactsList())
            }
            runOnUiThread{
                mainViewModel.contacts.observe(this, observer)
            }
        }.start()
    }
}
