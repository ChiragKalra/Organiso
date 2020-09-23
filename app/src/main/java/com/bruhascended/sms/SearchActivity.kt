package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.conversastion.MessageListViewAdaptor
import com.bruhascended.sms.ui.search.SearchListViewAdaptor
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*
import java.util.*
import kotlin.collections.ArrayList


class SearchActivity : AppCompatActivity() {
    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var visibleCategories: Array<Int>

    private var type = 0

    private fun drawAllMessagesListView() {
        val key = searchEditText.text.toString()
        progress.visibility = View.VISIBLE

        Thread {
            val res = ArrayList<Pair<Conversation, Message?>>()
            if (!key.isBlank()) {
                for (i in visibleCategories) {
                    for (con in mainViewModel.daos[i].loadAllSync()) {
                        var msgs: List<Message>
                        Room.databaseBuilder(
                            mContext, MessageDatabase::class.java, con.sender
                        ).build().apply {
                            msgs = manager().search("%${key.trim().toLowerCase(Locale.ROOT)}%")
                            close()
                        }
                        if (!msgs.isNullOrEmpty()) res.add(con to null)
                        for (msg in msgs) {
                            res.add(con to msg)
                        }
                    }
                }
            }
            runOnUiThread {
                searchListView.apply {
                    adapter = SearchListViewAdaptor(mContext, res)
                    visibility = View.VISIBLE
                    progress.visibility = View.GONE
                    onItemClickListener = OnItemClickListener { _, v, i, _ ->
                        if (res[i].second == null) {
                            startActivity(
                                Intent(mContext, ConversationActivity::class.java)
                                    .putExtra("ye", res[i].first)
                            )
                        } else {
                            startActivity(
                                Intent(mContext, ConversationActivity::class.java)
                                    .putExtra("ye", res[i].first)
                                    .putExtra("ID", res[i].second!!.id)
                                    .putExtra("POS", v.top)
                            )
                        }
                        finish()
                    }
                }
                if (res.isEmpty()) info.visibility = TextView.VISIBLE
                else info.visibility = TextView.GONE
            }
        }.start()
    }

    private fun drawConversationListView() {
        val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
        progress.visibility = View.VISIBLE

        Thread {
            val res = ArrayList<Conversation>()

            if (!key.isBlank()) {
                for (i in visibleCategories) {
                    res.addAll(mainViewModel.daos[i].findBySender("%${key}%"))
                }
            }
            runOnUiThread {
                searchListView.apply {
                    //adapter = ConversationListViewAdaptor(mContext, res.toList())
                    visibility = View.VISIBLE
                    progress.visibility = View.GONE
                    onItemClickListener = OnItemClickListener { _, _, i, _ ->
                        val intent = Intent(mContext, ConversationActivity::class.java)
                        intent.putExtra("ye", res[i])
                        startActivity(intent)
                    }
                }
                if (res.isEmpty()) info.visibility = TextView.VISIBLE
                else info.visibility = TextView.GONE
            }
        }.start()
    }

    private fun drawMessagesListView() {
        val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
        progress.visibility = View.VISIBLE

        Thread {
            val res = if (!key.isBlank()) activeConversationDao.search("%${key}%")
            else listOf()
            runOnUiThread {
                searchListView.apply {
                    isStackFromBottom = true
                    adapter = MessageListViewAdaptor(mContext, res)
                    visibility = View.VISIBLE
                    progress.visibility = View.GONE
                    onItemLongClickListener = OnItemLongClickListener { _, v, i, _ ->
                        val intent = Intent("MESSAGE_SELECTED")
                            .putExtra("ID", res[i].id)
                            .putExtra("POS", v.top)
                        setResult(RESULT_OK, intent)
                        finish()
                        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
                        true
                    }
                }

                if (res.isEmpty()) info.visibility = TextView.VISIBLE
                else info.visibility = TextView.GONE
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        type = when(intent.getStringExtra("type")) {
            "conversations" -> 0
            "messages" -> 1
            else -> 2
        }
        visibleCategories = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )

        searchEditText.requestFocus()
        inputManager.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)

        clear_text.setOnClickListener{
            searchEditText.setText("")
        }

        clear_text.visibility = if (searchEditText.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        var lastChange: Long
        searchEditText.doOnTextChanged { text, _, _, _ ->
            clear_text.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            lastChange = System.currentTimeMillis()
            Handler(Looper.getMainLooper()).postDelayed({
                val now = System.currentTimeMillis()
                if (now - lastChange > 500) when (type) {
                    0 -> drawConversationListView()
                    1 -> drawMessagesListView()
                    2 -> drawAllMessagesListView()
                }
            }, 600)
        }
        backButton.setOnClickListener{
            onBackPressed()
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
        super.onBackPressed()
    }
}