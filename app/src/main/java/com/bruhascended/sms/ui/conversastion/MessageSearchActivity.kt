package com.bruhascended.sms.ui.conversastion

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.sms.R
import com.bruhascended.sms.activeConversationDao
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class SearchActivity : AppCompatActivity() {
    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var visibleCategories: Array<Int>

    /*private fun drawConversationRecycler() {
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
    }*/

    private fun drawMessagesRecycler() {
        val mAdaptor = MessageRecyclerAdaptor(mContext)
        searchRecycler.apply {
            adapter = mAdaptor
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

            if (!key.isBlank()) {
                Thread{
                    val a = activeConversationDao.search("%$key%", "% $key%").isEmpty()
                    runOnUiThread{
                        info.isVisible = a
                    }
                }.start()
            }


            mAdaptor.searchKey = key
            val flow = Pager(PagingConfig(
                pageSize = 3,
                initialLoadSize = 3,
                prefetchDistance = 12,
                maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
            )) {
                activeConversationDao.searchPaged("$key%", "% $key%")
            }.flow.cachedIn(lifecycleScope)
            lifecycleScope.launch {
                flow.collectLatest {
                    mAdaptor.submitData(it)
                }
            }

            mAdaptor.notifyDataSetChanged()
            mAdaptor.onItemClickListener = {
                val intent = Intent("MESSAGE_SELECTED")
                    .putExtra("ID", it.message.id)
                    .putExtra("POS", it.root.top)
                setResult(RESULT_OK, intent)
                finish()
                overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
            }
            mAdaptor.onItemLongClickListener = { false }
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        visibleCategories = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
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

        drawMessagesRecycler()

    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
        super.onBackPressed()
    }
}