package com.bruhascended.organiso.ui.conversation

import android.content.Context
import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.data.ContactsManager.Companion.EXTRA_SENDER
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.organiso.settings.InterfaceFragment.Companion.setPrefTheme
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.ScrollEffectFactory
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class SearchActivity : AppCompatActivity() {

    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var activeConversationDao: MessageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_search)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val sender = intent.getStringExtra(EXTRA_SENDER)!!
        activeConversationDao = MessageDbFactory(this).of(sender).manager()

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
                maxSize = 120,
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
                setResult(RESULT_OK, intent)
                finish()
                overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
            }
            mAdaptor.onItemLongClickListener = { false }
            true
        }
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
        overridePendingTransition(R.anim.hold, android.R.anim.fade_out)
        super.onBackPressed()
    }
}