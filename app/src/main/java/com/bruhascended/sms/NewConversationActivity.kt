package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.listViewAdapter.ContactListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import java.net.URLDecoder
import java.util.*
import kotlin.collections.ArrayList


var memoryCache = HashMap<String, Bitmap?>()

class NewConversationActivity : AppCompatActivity() {

    private lateinit var to: EditText
    private lateinit var mContext: Context
    private lateinit var message: EditText

    private fun processIntentData(intent: Intent) {
        if (Intent.ACTION_SENDTO == intent.action) {
            var destinationNumber = intent.dataString
            destinationNumber = URLDecoder.decode(destinationNumber)
            destinationNumber = destinationNumber.replace("-", "")
                .replace("smsto:", "")
                .replace("sms:", "")
            to.setText(ContactsManager(mContext).getRaw(destinationNumber))
            message.requestFocus()
        } else if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
            val str = intent.getStringExtra(Intent.EXTRA_TEXT)
            message.setText(str)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_conversation)

        val contactListView: RecyclerView = findViewById(R.id.contactListView)
        val sendButton: ImageButton = findViewById(R.id.sendButton)
        val progress: ProgressBar = findViewById(R.id.progress)

        val llm = LinearLayoutManager(this)
        val clickAction = { contact: Contact ->
            to.setText(contact.number)
            to.setSelection(contact.number.length)
        }

        to = findViewById(R.id.toEditText)
        message = findViewById(R.id.messageEditText)
        mContext = this

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
                        ContactListViewAdaptor(filtered.toTypedArray(), this)
                    } else ContactListViewAdaptor(contacts, this)

                    adaptor.onItemClick = clickAction
                    contactListView.adapter = adaptor
                }

                processIntentData(intent)

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
            if (mainViewModel == null) {
                mainViewModel = MainViewModel()
                mainViewModel!!.contacts.postValue(ContactsManager(this).getContactsList())
            }
            runOnUiThread{
                mainViewModel!!.contacts.observe(this, observer)
            }
        }.start()
    }
}
