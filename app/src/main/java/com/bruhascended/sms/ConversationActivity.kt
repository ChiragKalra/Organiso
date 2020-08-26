package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.*
import com.bruhascended.sms.services.MMSSender
import com.bruhascended.sms.services.SMSSender
import com.bruhascended.sms.ui.MediaPreviewManager
import com.bruhascended.sms.ui.conversastion.MessageListViewAdaptor
import com.bruhascended.sms.ui.conversastion.MessageMultiChoiceModeListener
import com.bruhascended.sms.ui.main.MainViewModel
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

var conversationSender: String? = null
lateinit var conversationDao: MessageDao

class ConversationActivity : AppCompatActivity() {
    private lateinit var mdb: MessageDao

    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private var inputManager: InputMethodManager? = null

    private lateinit var mpm: MediaPreviewManager
    private lateinit var firebaseAnalytics: FirebaseAnalytics

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_theme", false)
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)

        setContentView(R.layout.activity_conversation)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        conversation = intent.getSerializableExtra("ye") as Conversation
        smsSender = SMSSender(this, conversation, sendButton)
        mmsSender = MMSSender(this, conversation, sendButton)
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)
        conversationSender = conversation.sender

        setSupportActionBar(toolbar)
        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
        }

        if (isMainViewModelNull()) {
            mainViewModel = MainViewModel()
            mainViewModel.daos = Array(6){
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        if (conversation.id != null) {
            conversation.read = true
            mainViewModel.daos[conversation.label].update(conversation)
        }

        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        conversationDao = mdb

        mpm = MediaPreviewManager(
            this,
            videoView,
            imagePreview,
            seekBar,
            playPauseButton,
            videoPlayPauseButton,
            addMedia
        )

        sendButton.setOnClickListener {
            if (mpm.mmsType > 0) {
                mmsSender.sendSMS(messageEditText.text.toString(), mpm.mmsURI, mpm.mmsTypeString)
                messageEditText.setText("")
                mpm.hideMediaPreview()
            } else if (messageEditText.text.toString().trim() != "") {
                smsSender.sendSMS(messageEditText.text.toString())
                messageEditText.setText("")
            }
        }

        if (conversation.id == null) {
            messageEditText.setText(conversation.lastSMS)
            if (intent.data != null) mpm.showMediaPreview(intent)
            sendButton.callOnClick()
        }

        addMedia.setOnClickListener{
            mpm.loadMedia()
        }

        mdb.loadAll().observe(this, {
            if(it.count() > 0) it.last().apply {
                conversation.lastSMS = text
                conversation.time = time
                conversation.lastMMS = path != null
                mainViewModel.daos[conversation.label].update(conversation)
            }
            val editListAdapter = MessageListViewAdaptor(this, it)
            listView.apply {
                val recyclerViewState = onSaveInstanceState()!!
                if (adapter != null && lastVisiblePosition == adapter.count - 1 &&
                    childCount > 0 && getChildAt(childCount - 1).bottom <= height) {
                    adapter = editListAdapter
                    smoothScrollToPosition(adapter.count - 1)
                } else {
                    adapter = editListAdapter
                    onRestoreInstanceState(recyclerViewState)
                }

                setMultiChoiceModeListener(
                    MessageMultiChoiceModeListener(mContext, this, mdb, conversation)
                )
            }
            progress.visibility = View.GONE
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name ?: conversation.sender
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.METHOD, "${conversation.label} to 5")
                        firebaseAnalytics.logEvent("${conversation.label}_to_5", bundle)
                        moveTo(conversation, 5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.METHOD, "${conversation.label} to 4")
                        firebaseAnalytics.logEvent("${conversation.label}_to_4", bundle)
                        reportSpam(this, conversation)
                        moveTo(conversation, 4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Do you want to delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.METHOD, "${conversation.label} to -1")
                        firebaseAnalytics.logEvent("${conversation.label}_to_-1", bundle)
                        moveTo(conversation, -1)
                        Toast.makeText(mContext, "Conversation Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        finish()
                    }
            }
            R.id.action_move -> {
                val choices = Array(4){mContext.resources.getString(labelText[it])}
                var selection = conversation.label
                AlertDialog.Builder(mContext)
                    .setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select -> selection = select}
                    .setPositiveButton("Move") { dialog, _ ->
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.METHOD, "${conversation.label} to $selection")
                        firebaseAnalytics.logEvent("${conversation.label}_to_$selection", bundle)
                        moveTo(conversation, selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
            }
            R.id.action_search -> {
                showSearchLayout()
                backButton.setOnClickListener{ hideSearchLayout() }
                null
            }
            R.id.action_mute -> {
                conversation.isMuted = !conversation.isMuted
                mainViewModel.daos[conversation.label].update(conversation)
                GlobalScope.launch {
                    delay(300)
                    runOnUiThread {
                        item.title = if (conversation.isMuted) "UnMute" else "Mute"
                    }
                }
                null
            }
            else -> null
        }?.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss()}?.create()?.show()
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && data != null && data.data != null) {
            mpm.showMediaPreview(data)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val muteItem = menu!!.findItem(R.id.action_mute)
        muteItem.title = if (conversation.isMuted) "UnMute" else "Mute"
        return super.onPrepareOptionsMenu(menu)
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
