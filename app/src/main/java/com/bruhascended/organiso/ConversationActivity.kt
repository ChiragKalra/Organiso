package com.bruhascended.organiso

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.bruhascended.organiso.analytics.AnalyticsLogger
import com.bruhascended.organiso.data.SMSManager.Companion.labelText
import com.bruhascended.organiso.db.*
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.ui.common.ListSelectionManager
import com.bruhascended.organiso.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.organiso.ui.common.MediaPreviewManager
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.conversation.MessageRecyclerAdaptor
import com.bruhascended.organiso.ui.conversation.MessageSelectionListener
import com.bruhascended.organiso.ui.conversation.SearchActivity
import com.bruhascended.organiso.ui.main.ConversationRecyclerAdaptor
import kotlinx.android.synthetic.main.activity_conversation.*
import kotlinx.android.synthetic.main.layout_send.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
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
class ConversationActivity : AppCompatActivity() {

    companion object {
        const val selectMediaArg = 0
        const val selectMessageArg = 1
    }

    private lateinit var mdb: MessageDao

    private lateinit var messages: List<Message>
    private lateinit var mContext: Context
    private lateinit var conversation: Conversation

    private lateinit var mAdaptor: MessageRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager

    private lateinit var smsSender: SMSSender
    private lateinit var mmsSender: MMSSender
    private var inputManager: InputMethodManager? = null

    private lateinit var mpm: MediaPreviewManager
    private lateinit var analyticsLogger: AnalyticsLogger
    private lateinit var selectionManager: ListSelectionManager<Message>

    private var goToBottomVisible = false
    private val goToBottomScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            val dp = resources.displayMetrics.density
            if (mLayoutManager.findFirstVisibleItemPosition() > 5 && !goToBottomVisible) {
                goToBottomVisible = true
                goToBottom.animate().alpha(1f).translationY(0f)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            } else if (mLayoutManager.findFirstVisibleItemPosition() <= 5 && goToBottomVisible) {
                goToBottomVisible = false
                goToBottom.animate().alpha(0f).translationY(48*dp)
                    .setInterpolator(AccelerateDecelerateInterpolator()).start()
            }
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        val roundPx = bitmap.width.toFloat()
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun getSenderIcon(): Icon {
        val bg = ContextCompat.getDrawable(mContext, R.drawable.bg_notification_icon)?.apply {
            setTint(mContext.getColor(ConversationRecyclerAdaptor.colorRes[(conversation.id!! % ConversationRecyclerAdaptor.colorRes.size).toInt()]))
        }

        return when {
            conversation.sender.first().isLetter() -> {
                val bot = ContextCompat.getDrawable(mContext, R.drawable.ic_bot)
                val finalDrawable = LayerDrawable(arrayOf(bg, bot))
                finalDrawable.setLayerGravity(1, Gravity.CENTER)
                Icon.createWithBitmap(finalDrawable.toBitmap())
            }
            conversation.dp.isNotBlank() -> {
                val dp = getRoundedCornerBitmap(BitmapFactory.decodeFile(conversation.dp)!!)
                Icon.createWithBitmap(dp)
            }
            else -> {
                val person = ContextCompat.getDrawable(mContext, R.drawable.ic_person)
                val finalDrawable = LayerDrawable(arrayOf(bg, person))
                finalDrawable.setLayerGravity(1, Gravity.CENTER)
                Icon.createWithBitmap(finalDrawable.toBitmap())
            }
        }
    }

    private fun init() {
        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        conversation = intent.getSerializableExtra("ye") as Conversation? ?:
            Conversation(
                null,
                intent.getStringExtra("sender")!!,
                intent.getStringExtra("name"),
                "",
                true,
                0,
                "",
                0,
                -1,
                FloatArray(5){0F}
            )

        requireMainViewModel(this)
        if (conversation.lastSMS.isBlank()) {
            for (i in 0..4) {
                val res = mainViewModel.daos[i].findBySender(conversation.sender)
                if (res.isNotEmpty()) {
                    conversation = res.first()
                    break
                }
            }
        }

        smsSender = SMSSender(this, arrayOf(conversation))
        mmsSender = MMSSender(this, arrayOf(conversation))
        analyticsLogger = AnalyticsLogger(this)
        mdb = Room.databaseBuilder(
            this, MessageDatabase::class.java, conversation.sender
        ).allowMainThreadQueries().build().manager()
        activeConversationDao = mdb
        activeConversationSender = conversation.sender

        mpm = MediaPreviewManager(
            this,
            videoView,
            imagePreview,
            seekBar,
            playPauseButton,
            videoPlayPauseButton,
            addMedia
        )
    }

    private var scroll = true
    private fun setupRecycler() {
        activeConversationDao.loadAll().observe(this, {
            messages = it
            if (scroll) {
                scroll = false
                val id = intent?.getLongExtra("ID", -1L) ?: -1L
                if (id != -1L) {
                    recyclerView.postDelayed({
                        scrollToItem(id)
                    }, 200)
                }
            }
        })

        val flow = Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 60,
                maxSize = 180,
            )
        ) {
            mdb.loadAllPaged()
        }.flow.cachedIn(lifecycleScope)

        mAdaptor = MessageRecyclerAdaptor(mContext, smsSender, mmsSender)
        val mListener =  MessageSelectionListener(mContext, conversation)
        selectionManager = ListSelectionManager(
            mContext as AppCompatActivity,
            mAdaptor as SelectionRecyclerAdaptor<Message, RecyclerView.ViewHolder>,
            mListener
        )
        mAdaptor.selectionManager = selectionManager
        mListener.selectionManager = selectionManager
        recyclerView.apply {
            conversationLayout.doOnLayout {
                layoutParams.height = sendLayout.top - appBarLayout.bottom
            }
            mLayoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
                reverseLayout = true
            }
            layoutManager = mLayoutManager
            adapter = mAdaptor
            recyclerView.edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
            addOnScrollListener(goToBottomScrollListener)
            goToBottom.setOnClickListener {
                smoothScrollToPosition(0)
            }
        }

        lifecycleScope.launch {
            flow.collectLatest {
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

    private fun trackLastMessage() {
        mdb.loadLast().observe(this, {
            if (it != null) {
                if (conversation.lastSMS != it.text ||
                    conversation.time != it.time ||
                    conversation.lastMMS != (it.path != null)
                ) {
                    conversation.lastSMS = it.text
                    conversation.time = it.time
                    conversation.lastMMS = it.path != null
                    mainViewModel.daos[conversation.label].update(conversation)
                }
            } else {
                mainViewModel.daos[conversation.label].delete(conversation)
            }
        })
    }

    private fun scrollToItem(id: Long) {
        val index = messages.indexOfFirst { m -> m.id==id }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
            "dark_theme",
            false
        )
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_conversation)
        setSupportActionBar(toolbar)
        init()

        supportActionBar!!.title = conversation.name ?: conversation.sender
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)

        if (!conversation.sender.first().isDigit()) {
            sendLayout.visibility = LinearLayout.INVISIBLE
            notSupported.visibility = TextView.VISIBLE
        }

        sendButton.setOnClickListener {
            if (mpm.mmsType > 0) {
                sendButton.isEnabled = false
                mmsSender.sendMMS(messageEditText.text.toString(), mpm.mmsURI, mpm.mmsTypeString)
                sendButton.isEnabled = true
                messageEditText.setText("")
                mpm.hideMediaPreview()
            } else if (messageEditText.text.toString().trim() != "") {
                sendButton.isEnabled = false
                smsSender.sendSMS(messageEditText.text.toString())
                sendButton.isEnabled = true
                messageEditText.setText("")
            }
        }

        if (conversation.id != null) {
            if (!conversation.read) {
                conversation.read = true
                mainViewModel.daos[conversation.label].update(conversation)
            }
        } else if (!conversation.lastSMS.isBlank()) {
            messageEditText.setText(conversation.lastSMS)
            if (intent.data != null) mpm.showMediaPreview(intent)
            sendButton.callOnClick()
        } else if (intent.data != null) {
            mpm.showMediaPreview(intent)
            sendButton.callOnClick()
        }

        addMedia.setOnClickListener{
            mpm.loadMedia()
        }
        setupRecycler()
        trackLastMessage()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val display = conversation.name ?: conversation.sender
        if (item.itemId == android.R.id.home) onBackPressed()
        when (item.itemId) {
            R.id.action_block -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Block $display?")
                    .setPositiveButton("Block") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_5")
                        conversation.moveTo(5)
                        Toast.makeText(mContext, "Sender Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Report $display as spam?")
                    .setPositiveButton("Report") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_4")
                        analyticsLogger.reportSpam(conversation)
                        conversation.moveTo(4)
                        Toast.makeText(mContext, "Sender Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Delete this conversation?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_-1")
                        conversation.moveTo(-1, mContext)
                        Toast.makeText(mContext, "Conversation Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        finish()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_move -> {
                val choices = ArrayList<String>().apply {
                    for (i in 0..3) {
                        if (i != conversation.label) add(mContext.resources.getString(labelText[i]))
                    }
                }.toTypedArray()
                var selection = 0
                AlertDialog.Builder(mContext)
                    .setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select ->
                        selection = select + if (select >= conversation.label) 1 else 0
                    }
                    .setPositiveButton("Move") { dialog, _ ->
                        analyticsLogger.log("${conversation.label}_to_$selection")
                        conversation.moveTo(selection)
                        Toast.makeText(mContext, "Conversation Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.create().show()
            }
            R.id.action_search -> {
                startActivityForResult(
                    Intent(mContext, SearchActivity::class.java),
                    selectMessageArg
                )
                overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
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
            }
            R.id.action_call -> {
                val intent = Intent(Intent.ACTION_DIAL)
                intent.data = Uri.parse("tel:${conversation.sender}")
                startActivity(intent)
            }
            R.id.action_contact -> {
                val intent = Intent(
                    ContactsContract.Intents.SHOW_OR_CREATE_CONTACT,
                    Uri.parse("tel:" + conversation.sender)
                )
                startActivity(intent)
            }
            R.id.action_create_shortcut -> {
                val shortcutManager = getSystemService(ShortcutManager::class.java)!!

                if (shortcutManager.isRequestPinShortcutSupported) {
                    val pinShortcutInfo =
                        ShortcutInfo.Builder(mContext, conversation.sender)
                            .setIcon(getSenderIcon())
                            .setShortLabel(conversation.name ?: conversation.sender)
                            .setIntent(
                                Intent(mContext, ConversationActivity::class.java)
                                    .setAction("android.intent.action.VIEW")
                                    .putExtra("name", conversation.name)
                                    .putExtra("sender", conversation.sender))
                            .setCategories(setOf("android.shortcut.conversation"))
                        .build()

                    shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                }
            }
            android.R.id.home -> {
                startActivityIfNeeded(
                    Intent(mContext, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT), 0
                )
                finish()
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == selectMediaArg && data != null && data.data != null) {
            mpm.showMediaPreview(data)
        } else if (requestCode == selectMessageArg && resultCode == RESULT_OK && data != null) {
            val id = data.getLongExtra("ID", -1L)
            if (id != -1L) scrollToItem(id)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val muteItem = menu.findItem(R.id.action_mute)
        val callItem = menu.findItem(R.id.action_call)
        val contactItem = menu.findItem(R.id.action_contact)

        muteItem.title = if (conversation.isMuted) "UnMute" else "Mute"
        callItem.isVisible = conversation.sender.first().isDigit()
        contactItem.isVisible = conversation.sender.first().isDigit()
        if (conversation.name != null) contactItem.title = "View Contact"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation, menu)
        return true
    }

    override fun onPause() {
       activeConversationSender = null
        super.onPause()
    }

    override fun onResume() {
        if (conversation.id != null)
            NotificationManagerCompat.from(this).cancel(conversation.id!!.toInt())
        activeConversationSender = conversation.sender
        super.onResume()
    }
}
