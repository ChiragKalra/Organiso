@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseBooleanArray
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AbsListView.MultiChoiceModeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.services.SMSSender
import com.bruhascended.sms.ui.listViewAdapter.MessageListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*


var conversationSender: String? = null
lateinit var conversationDao: MessageDao

class ConversationActivity : AppCompatActivity() {
    lateinit var mdb: MessageDao

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var messageEditText: EditText
    private lateinit var searchLayout: LinearLayout
    private lateinit var backButton: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var listView: ListView
    private lateinit var loading: ProgressBar
    private lateinit var sendLayout: LinearLayout
    private lateinit var notSupport: TextView
    private lateinit var sendButton: ImageButton
    private lateinit var toolbar: Toolbar
    private lateinit var smsSender: SMSSender
    private var inputManager: InputMethodManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        mContext = this
        notSupport = findViewById(R.id.notSupported)
        toolbar = findViewById(R.id.toolbar)
        sendLayout = findViewById(R.id.sendLayout)
        listView = findViewById(R.id.messageListView)
        messageEditText = findViewById(R.id.messageEditText)
        backButton = findViewById(R.id.cancelSearch)
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        loading = findViewById(R.id.progress)
        sendButton = findViewById(R.id.sendButton)
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        conversation = intent.getSerializableExtra("ye") as Conversation
        smsSender = SMSSender(this, conversation, sendButton)
        conversationSender = conversation.sender

        setSupportActionBar(toolbar)
        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupport.visibility = TextView.VISIBLE
        }

        if (mainViewModel == null) {
            mainViewModel = MainViewModel()
        }
        if (mainViewModel!!.daos == null) {
            mainViewModel!!.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }
        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        conversationDao = mdb

        sendButton.setOnClickListener {
            if (messageEditText.text.toString().trim() != "") {
                smsSender.sendSMS(messageEditText.text.toString())
                messageEditText.setText("")
            }
        }
        if (conversation.id == null) {
            messageEditText.setText(conversation.lastSMS)
            sendButton.callOnClick()
        }

        var recyclerViewState: Parcelable
        mdb.loadAll().observe(this, Observer<List<Message>> {
            val editListAdapter = MessageListViewAdaptor(this, it)
            recyclerViewState = listView.onSaveInstanceState()!!
            if (
                listView.adapter != null &&
                listView.lastVisiblePosition == listView.adapter.count-1 &&
                listView.childCount > 0 &&
                listView.getChildAt(listView.childCount-1).bottom <= listView.height
            ) {
                listView.adapter = editListAdapter
                listView.smoothScrollToPosition(listView.adapter.count-1)
            } else {
                listView.adapter = editListAdapter
                listView.onRestoreInstanceState(recyclerViewState)
            }

            var rangeSelect = false
            var previousSelected = -1
            listView.setMultiChoiceModeListener(object : MultiChoiceModeListener {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                override fun onDestroyActionMode(mode: ActionMode) {
                    editListAdapter.removeSelection()
                }

                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    mode.menuInflater.inflate(R.menu.message_selection, menu)
                    rangeSelect = false
                    previousSelected = -1
                    return true
                }

                @SuppressLint("InflateParams")
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        R.id.action_delete -> {
                            AlertDialog.Builder(mContext).setTitle("Do you want to delete selected messages?")
                                .setPositiveButton("Delete") { dialog, _ ->
                                    val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
                                    for (i in 0 until selected.size()) {
                                        if (selected.valueAt(i)) {
                                            val selectedItem: Message = editListAdapter.getItem(selected.keyAt(i))
                                            mdb.delete(selectedItem)
                                        }
                                    }
                                    if (listView.checkedItemCount == it.size) {
                                        moveTo(conversation, -1, mContext)
                                        (mContext as ConversationActivity).finish()
                                    }
                                    Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .create().show()
                            true
                        }
                        R.id.action_select_range -> {
                            val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                            val iv = inflater.inflate(R.layout.view_button_transition, null) as ImageView

                            if (rangeSelect) iv.setImageResource(R.drawable.range_to_single)
                            else iv.setImageResource(R.drawable.single_to_range)
                            item.actionView = iv
                            (iv.drawable as AnimatedVectorDrawable).start()

                            GlobalScope.launch {
                                delay(300)
                                if (rangeSelect) {
                                    item.setIcon(R.drawable.ic_single)
                                } else {
                                    item.setIcon(R.drawable.ic_range)
                                }
                                item.actionView = null
                                rangeSelect = !rangeSelect
                                if (rangeSelect) previousSelected = -1
                            }
                            true
                        }
                        R.id.action_copy -> {
                            val clipboard: ClipboardManager =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
                            val sb = StringBuilder()
                            for (i in 0 until selected.size()) {
                                if (selected.valueAt(i)) {
                                    val selectedItem: Message = editListAdapter.getItem(selected.keyAt(i))
                                    sb.append(selectedItem.text).append('\n')
                                }
                            }
                            val clip: ClipData = ClipData.newPlainText("none", sb.toString())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(mContext, "Copied", Toast.LENGTH_LONG).show()
                            mode.finish()
                            true
                        }
                        else -> false
                    }
                }

                override fun onItemCheckedStateChanged(
                    mode: ActionMode, position: Int, id: Long, checked: Boolean
                ) {
                    if (rangeSelect) {
                        previousSelected = if (previousSelected == -1) {
                            position
                        } else {
                            val low = min(previousSelected, position) + 1
                            val high = max(previousSelected, position) - 1
                            for (i in low..high) {
                                listView.setItemChecked(i, !listView.isItemChecked(i))
                                editListAdapter.toggleSelection(i)
                            }
                            -1
                        }
                    }
                    editListAdapter.toggleSelection(position)
                    mode.title = "${listView.checkedItemCount} selected"
                }
            })
            progress.visibility = View.GONE
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name?: conversation.sender
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        moveTo(conversation, 5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        moveTo(conversation, 4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        moveTo(conversation, -1)
                        Toast.makeText(mContext, "Conversation Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        finish()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_move -> {
                val choices = Array(4){mContext.resources.getString(labelText[it])}

                var selection = conversation.label
                AlertDialog.Builder(mContext).setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select -> selection = select}
                    .setPositiveButton("Move") { dialog, _ ->
                        moveTo(conversation, selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}
                    .create().show()
            }
            R.id.action_search -> {
                showSearchLayout()
                backButton.setOnClickListener{ hideSearchLayout() }
            }
        }
        return false
    }

    private fun showSearchLayout() {
        searchLayout.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        searchEditText.requestFocus()
                        inputManager?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                        notSupport.visibility = TextView.GONE
                        sendLayout.visibility = LinearLayout.GONE
                    }
                })
        }

        searchEditText.doOnTextChanged { _, _, _, _ ->
            val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
            progress.visibility = View.VISIBLE
            if (key.isNotEmpty()) {
                listView.adapter = MessageListViewAdaptor(mContext, mdb.search("%${key}%"))
            }
            progress.visibility = View.GONE
        }
    }

    private fun hideSearchLayout() {
        progress.visibility = View.VISIBLE
        if (!conversation.sender.first().isDigit()) {
            notSupport.visibility = TextView.VISIBLE
        } else {
            sendLayout.visibility = LinearLayout.VISIBLE
        }

        searchLayout.animate()
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    searchLayout.visibility = View.GONE
                    inputManager?.hideSoftInputFromWindow(backButton.windowToken, 0)
                }
            }).start()


        mdb.loadAll().observe(mContext as AppCompatActivity, object: Observer<List<Message>> {
            override fun onChanged(t: List<Message>?) {
                listView.adapter = MessageListViewAdaptor(mContext, t!!)
                progress.visibility = View.GONE
                mdb.loadAll().removeObserver(this)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onPause() {
        conversationSender = null
        super.onPause()
    }

    override fun onResume() {
        conversationSender = conversation.sender
        super.onResume()
    }

    override fun onDestroy() {
        conversationSender = null
        super.onDestroy()
    }
}
