package com.bruhascended.sms

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDatabase
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import com.bruhascended.sms.ui.search.SearchRecyclerAdaptor
import com.bruhascended.sms.ui.search.SearchResultViewHolder.ResultItem
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*


class SearchActivity : AppCompatActivity() {

    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var visibleCategories: Array<Int>
    private lateinit var mAdaptor: SearchRecyclerAdaptor
    private lateinit var hiddenCategories: Array<Int>

    private fun append(item: ResultItem) {
        searchRecycler.post {
            mAdaptor.addItem(item)
        }
    }

    private fun showResults(key: String) {
        val result = arrayListOf<ResultItem>()
        mAdaptor = SearchRecyclerAdaptor(mContext, key, result)
        searchRecycler.adapter = mAdaptor

        Thread {
            for (categories in arrayOf(visibleCategories, hiddenCategories)) {
                for (category in categories) {
                    val cons = mainViewModel.daos[category].search("$key%", "% $key%")
                    if (cons.isNotEmpty()) {
                        append(ResultItem(4, categoryHeader = category))
                        for (con in cons) {
                            append(ResultItem(0, conversation = con))
                        }
                    }
                }
            }

            for (categories in arrayOf(visibleCategories, hiddenCategories)) {
                for (category in categories) {
                    var isEmpty = true
                    for (con in mainViewModel.daos[category].loadAllSync()) {
                        var msgs: List<Message>
                        Room.databaseBuilder(
                            mContext, MessageDatabase::class.java, con.sender
                        ).build().apply {
                            msgs = manager().search("%$key%", "% $key%")
                            close()
                        }
                        if (!msgs.isNullOrEmpty()) {
                            if (isEmpty) {
                                isEmpty = false
                                append(ResultItem(4, categoryHeader = 10+category))
                            }
                            append(ResultItem(1, conversation = con))
                            for (msg in msgs) {
                                append(ResultItem(2, conversation = con, message = msg))
                            }
                        }
                    }
                }
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        if (prefs.getBoolean("dark_theme", false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)
        setContentView(R.layout.activity_search)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        visibleCategories = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )

        hiddenCategories = Gson().fromJson(
            prefs.getString("hidden_categories", ""), Array<Int>::class.java
        )

        searchEditText.requestFocus()
        inputManager.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)

        clear_text.setOnClickListener{
            searchEditText.setText("")
            searchEditText.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }

        backButton.setOnClickListener{
            onBackPressed()
        }

        clear_text.visibility = View.GONE
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
            val key = searchEditText.text.toString().trim()

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