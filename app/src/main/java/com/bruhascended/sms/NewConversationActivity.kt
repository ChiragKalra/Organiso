package com.bruhascended.sms

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.listViewAdapter.ContactListViewAdaptor
import java.util.*
import kotlin.collections.ArrayList

class NewConversationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_conversation)

        val contactListView: RecyclerView = findViewById(R.id.contactListView)
        val to: EditText = findViewById(R.id.toEditText)
        val message: EditText = findViewById(R.id.messageEditText)
        val sendButton: ImageButton = findViewById(R.id.sendButton)
        val progress: ProgressBar = findViewById(R.id.progress)


        val llm = LinearLayoutManager(this)
        val clickAction = { contact: Contact ->
            to.setText(contact.number)
            to.setSelection(contact.number.length)
        }

        to.requestFocus()
        supportActionBar!!.title = "New Conversation"
        llm.orientation = LinearLayoutManager.HORIZONTAL

        mainViewModel!!.contacts.observe(this, Observer {
            if (it != null) {
                val contacts = it

                var adaptor = ContactListViewAdaptor(contacts)
                adaptor.onItemClick = clickAction

                contactListView.layoutManager = llm
                contactListView.adapter = adaptor
                contactListView.visibility = RecyclerView.VISIBLE
                progress.visibility = ProgressBar.INVISIBLE

                to.doOnTextChanged { _, _, _, _ ->
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
                        ContactListViewAdaptor(filtered.toTypedArray())
                    } else ContactListViewAdaptor(contacts)

                    adaptor.onItemClick = clickAction
                    contactListView.adapter = adaptor
                }

                sendButton.setOnClickListener {
                    if (message.text.toString().trim() != "") {
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
                                message.text.toString().trim(),
                                0
                            )
                        )
                        startActivity(intent)
                        this.finish()
                    }
                }
            }
        })
    }
}
