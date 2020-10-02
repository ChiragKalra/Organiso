package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import com.bruhascended.sms.ui.search.SearchRecyclerAdaptor
import com.bruhascended.sms.ui.search.SearchResultViewHolder.ResultItem
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*
import java.util.*


class SearchActivity : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var categories: Array<Int>
    private lateinit var mAdaptor: SearchRecyclerAdaptor
    private lateinit var result: ArrayList<ResultItem>

    private var searchThread = Thread{}

    private fun showResults(key: String) {
        searchRecycler.scrollToPosition(0)
        searchRecycler.isVisible = true
        searchThread.interrupt()
        mAdaptor.searchKey = key
        mAdaptor.refresh()
        searchThread = Thread {
            val displayedSenders = arrayListOf<String>()
            for (category in categories) {
                if (searchThread.isInterrupted) return@Thread
                val cons = mainViewModel.daos[category].search("$key%", "% $key%")
                if (cons.isNotEmpty()) {ResultItem(4, categoryHeader = category)
                    cons.forEach { displayedSenders.add(it.sender) }
                    searchRecycler.post {
                        mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = category)))
                        mAdaptor.addItems(
                            List(cons.size) {
                                ResultItem(0, conversation = cons[it])
                            }
                        )
                    }
                }
            }

            var otherDisplayed = false
            mainViewModel.contacts.value?.forEach {  contact ->
                if (key !in contact.name.toLowerCase(Locale.ROOT) &&
                    key !in contact.number.toLowerCase(Locale.ROOT))
                    return@forEach

                for (sender in displayedSenders) {
                    if (sender == contact.number) {
                        return@forEach
                    }
                }

                if (!otherDisplayed) {
                    otherDisplayed = true
                    searchRecycler.post {
                        mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = 42)))
                    }
                }
                searchRecycler.post {
                    mAdaptor.addItems(listOf(ResultItem(1,
                        conversation = Conversation(
                            null,
                            contact.number,
                            contact.name,
                            "",
                            true,
                            0,
                            "",
                            0,
                            -1,
                            FloatArray(5) { its ->
                                if (its == 0) 1f else 0f
                            }
                        )
                    )))
                }
            }

            for (category in categories) {
                var isEmpty = true
                if (searchThread.isInterrupted) return@Thread
                for (con in mainViewModel.daos[category].loadAllSync()) {
                    var msgs: List<Message>
                    if (searchThread.isInterrupted) return@Thread
                    Room.databaseBuilder(
                        mContext, MessageDatabase::class.java, con.sender
                    ).build().apply {
                        msgs = manager().search("$key%", "% $key%")
                        close()
                    }
                    if (!msgs.isNullOrEmpty()) {
                        if (isEmpty) {
                            isEmpty = false
                            searchRecycler.post {
                                mAdaptor.addItems(listOf(ResultItem(4, categoryHeader = 10+category)))
                            }
                        }
                        searchRecycler.post {
                            mAdaptor.addItems(listOf(ResultItem(1, conversation = con)))
                            mAdaptor.addItems(
                                List(msgs.size) {
                                    ResultItem(2, conversation = con, message = msgs[it])
                                }
                            )
                        }
                    }
                    if (searchThread.isInterrupted) return@Thread
                }
            }

            searchRecycler.post {
                mAdaptor.doOnLoaded()
            }
        }
        searchThread.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean("dark_theme", false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)
        setContentView(R.layout.activity_search)
        searchEditText.requestFocus()

        mContext = this

        result = arrayListOf(ResultItem(5))
        mAdaptor = SearchRecyclerAdaptor(mContext, result)
        mAdaptor.doOnConversationClick = {
            startActivity(
                Intent(mContext, ConversationActivity::class.java)
                    .putExtra("ye", it)
            )
            finish()
        }

        mAdaptor.doOnMessageClick = {
            startActivity(
                Intent(mContext, ConversationActivity::class.java)
                    .putExtra("ye", it.second)
                    .putExtra("ID", it.first)
            )
            finish()
        }

        searchRecycler.adapter = mAdaptor

        val visible = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )
        val hidden = Gson().fromJson(
            prefs.getString("hidden_categories", ""), Array<Int>::class.java
        )
        categories = if (prefs.getBoolean("show_hidden_results", false))
            visible + hidden else visible

        clear_text.setOnClickListener{
            searchEditText.setText("")
            searchEditText.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }

        backButton.setOnClickListener{
            onBackPressed()
        }

        searchEditText.doOnTextChanged { key, _, _, _ ->
            clear_text.visibility = if (key.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        searchRecycler.apply {
            layoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
        }

        searchEditText.setOnEditorActionListener { _, i, _ ->
            if (i != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener true
            val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)

            searchRecycler.isVisible = !key.isBlank()
            if (key.isBlank()) {
                return@setOnEditorActionListener true
            } else {
                showResults(key)
            }

            true
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
    }
}