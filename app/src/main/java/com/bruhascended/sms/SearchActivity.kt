package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.activeConversationDao
import com.bruhascended.sms.ui.conversastion.MessageListViewAdaptor
import com.bruhascended.sms.ui.main.ConversationListViewAdaptor
import com.bruhascended.sms.ui.mainViewModel
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_search.*
import java.util.*
import kotlin.collections.ArrayList

class SearchActivity : AppCompatActivity() {
    private lateinit var inputManager: InputMethodManager
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var visibleCategories: Array<Int>
    private var conversationSearch = false

    private fun drawConversationListView() {
        val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
        progress.visibility = View.VISIBLE

        Thread {
            val res = ArrayList<Conversation>()

            for (i in visibleCategories) {
                res.addAll(mainViewModel.daos[i].findBySender("%${key}%"))
            }
            runOnUiThread {
                searchListView.apply {
                    adapter = ConversationListViewAdaptor(mContext, res.toList())
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
            val res = activeConversationDao.search("%${key}%")
            runOnUiThread {
                searchListView.apply {
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
        conversationSearch = intent.getStringExtra("type") == "conversations"
        visibleCategories = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )

        searchEditText.requestFocus()
        inputManager.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)

        clear_text.setOnClickListener{
            searchEditText.setText("")
        }

        searchEditText.doOnTextChanged { _, _, _, _ ->
            if (conversationSearch) drawConversationListView()
            else drawMessagesListView()
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