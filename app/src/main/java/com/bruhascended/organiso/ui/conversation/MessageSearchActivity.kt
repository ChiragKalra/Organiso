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
import androidx.paging.filter
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.constants.EXTRA_MESSAGE_ID
import com.bruhascended.core.constants.EXTRA_NUMBER
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.bruhascended.organiso.common.setPrefTheme
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class MessageSearchActivity : AppCompatActivity() {

    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var activeConversationDao: MessageDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_search)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val sender = intent.getStringExtra(EXTRA_NUMBER)!!
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
            searchRecycler.isVisible = key.isNotBlank()

            if (key.isBlank()) {
                return@setOnEditorActionListener true
            }


            info.isVisible = true
            mAdaptor.searchKey = key
            val flow = Pager(PagingConfig(
                pageSize = 12,
                initialLoadSize = 12,
                prefetchDistance = 24,
                maxSize = 120,
            )) {
                activeConversationDao.loadAllPaged()
            }.flow.cachedIn(lifecycleScope)
            lifecycleScope.launch {
                flow.collectLatest {
                    mAdaptor.submitData(it.filter { msg ->
                        Regex("\\b${key}", RegexOption.IGNORE_CASE).find(msg.text) != null
                    })
                    info.isVisible = mAdaptor.snapshot().size > 0
                }
            }

            mAdaptor.notifyDataSetChanged()
            mAdaptor.onItemClickListener = {
                val intent = Intent("MESSAGE_SELECTED")
                    .putExtra(EXTRA_MESSAGE_ID, it.message.id)
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