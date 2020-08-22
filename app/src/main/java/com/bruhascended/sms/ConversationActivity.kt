@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.util.SparseBooleanArray
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AbsListView.MultiChoiceModeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.services.MMSSender
import com.bruhascended.sms.services.SMSSender
import com.bruhascended.sms.ui.listViewAdapter.MessageListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.layout_send.*
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

    private var mmsType = 0
    private lateinit var mmsTypeString: String
    private lateinit var mmsURI: Uri
    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private lateinit var mp: MediaPlayer
    private lateinit var inflater: LayoutInflater
    private var inputManager: InputMethodManager? = null

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
                        notSupported.visibility = TextView.GONE
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
            notSupported.visibility = TextView.VISIBLE
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

    private fun loadMedia() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "audio/*", "video/*"))
        startActivityForResult(intent, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mp = MediaPlayer()
        conversation = intent.getSerializableExtra("ye") as Conversation
        smsSender = SMSSender(this, conversation, sendButton)
        mmsSender = MMSSender(this, conversation, sendButton)
        conversationSender = conversation.sender

        setSupportActionBar(toolbar)
        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
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
            if (mmsType > 0) {
                mmsSender.sendSMS(messageEditText.text.toString(), mmsURI, mmsTypeString)
                messageEditText.setText("")
                hideMediaPreview()
            } else if (messageEditText.text.toString().trim() != "") {
                smsSender.sendSMS(messageEditText.text.toString())
                messageEditText.setText("")
            }
        }
        if (conversation.id == null) {
            messageEditText.setText(conversation.lastSMS)
            sendButton.callOnClick()
        }

        addMedia.setOnClickListener{
            loadMedia()
        }

        var recyclerViewState: Parcelable
        mdb.loadAll().observe(this, {
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
                            val iv = inflater.inflate(R.layout.view_button_transition, null) as ImageView

                            if (rangeSelect) iv.setImageResource(R.drawable.range_to_single)
                            else iv.setImageResource(R.drawable.single_to_range)
                            item.actionView = iv
                            (iv.drawable as AnimatedVectorDrawable).start()


                            rangeSelect = !rangeSelect
                            if (rangeSelect) previousSelected = -1

                            GlobalScope.launch {
                                delay(300)
                                runOnUiThread {
                                    if (!rangeSelect) {
                                        item.setIcon(R.drawable.ic_single)
                                    } else {
                                        item.setIcon(R.drawable.ic_range)
                                    }
                                    item.actionView = null
                                }
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

    private fun fadeAway(view: View) {
        view.apply {
            if (visibility == View.VISIBLE) {
                alpha = 1f
                animate()
                    .alpha(0f)
                    .setDuration(300)
                    .start()
                GlobalScope.launch {
                    delay(400)
                    runOnUiThread {
                        visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun hideMediaPreview() {
        videoView.stopPlayback()
        mp.reset()
        fadeAway(videoView)
        fadeAway(imagePreview)
        fadeAway(seekBar)
        fadeAway(playPauseButton)
        fadeAway(videoPlayPauseButton)
        mmsType = 0
        addMedia.setImageResource(R.drawable.close_to_add)
        (addMedia.drawable as AnimatedVectorDrawable).start()
        addMedia.setOnClickListener {
            loadMedia()
        }
    }

    private fun showMediaPreview(data: Intent) {
        addMedia.apply {
            setImageResource(R.drawable.close)
            setOnClickListener {
                hideMediaPreview()
            }
        }

        mmsURI = data.data!!
        mmsTypeString = mContext.contentResolver.getType(mmsURI)!!
        mmsType = when {
            mmsTypeString.startsWith("image") -> {
                imagePreview.visibility = View.VISIBLE
                imagePreview.setImageURI(mmsURI)
                1
            }
            mmsTypeString.startsWith("audio") -> {
                seekBar.visibility = View.VISIBLE
                playPauseButton.visibility = View.VISIBLE
                mp.setDataSource(this, mmsURI)
                mp.prepare()
                seekBar.max = mp.duration/500

                val mHandler = Handler(mainLooper)
                runOnUiThread(object : Runnable {
                    override fun run() {
                        seekBar.progress = mp.currentPosition / 500
                        mHandler.postDelayed(this, 500)
                    }
                })

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onProgressChanged(
                        seekBar: SeekBar, progress: Int, fromUser: Boolean
                    ) {
                        if (fromUser) mp.seekTo(progress * 500)
                    }
                })

                playPauseButton.apply {
                    setOnClickListener {
                        if (mp.isPlaying) {
                            mp.pause()
                            setImageResource(R.drawable.ic_play)
                        } else {
                            mp.start()
                            setImageResource(R.drawable.ic_pause)
                        }
                    }
                }
                2
            }
            mmsTypeString.startsWith("video") -> {
                videoView.apply {
                    visibility = View.VISIBLE
                    videoPlayPauseButton.visibility = View.VISIBLE
                    setVideoURI(mmsURI)
                    setOnPreparedListener { mp -> mp.isLooping = true }
                    videoPlayPauseButton.setOnClickListener {
                        if (isPlaying) {
                            pause()
                            videoPlayPauseButton.setImageResource(R.drawable.ic_play)
                        } else {
                            start()
                            videoPlayPauseButton.setImageResource(R.drawable.ic_pause)
                        }
                    }
                }
                3
            }
            else -> 0
        }
        if (mmsType == 0) {
            hideMediaPreview()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && data != null && data.data != null) {
            showMediaPreview(data)
        }
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
