package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.doOnTextChanged
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.ui.listViewAdapter.ConversationListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import com.bruhascended.sms.ui.main.SectionsPagerAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.android.synthetic.main.activity_conversation.*
import java.util.*
import kotlin.collections.ArrayList


var mainViewModel: MainViewModel? = null

fun moveTo(conversation: Conversation, to: Int) {
    Thread( Runnable {
        mainViewModel!!.daos[conversation.label].delete(conversation)
        if (to >= 0) {
            conversation.id = null
            conversation.label = to
            conversation.forceLabel = to
            mainViewModel!!.daos[to].insert(conversation)
        }
    }).start()
}

fun getNewMessages(mContext: Context) {
    Thread( Runnable {
        val manager = SMSManager(mContext)
        manager.getMessages()
        manager.getLabels(null)
        manager.saveMessages()
    }).start()
}

fun getContacts(mContext: Context) {
    Thread ( Runnable {
        mainViewModel!!.contacts.postValue(ContactsManager(mContext).getContactsList())
    }).start()
}

class MainActivity : AppCompatActivity() {
    private lateinit var mContext: Context

    private lateinit var searchLayout: ConstraintLayout
    private lateinit var backButton: ImageButton
    private lateinit var searchEditText: EditText
    private lateinit var loading: ProgressBar
    private lateinit var appBar: AppBarLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var viewPager: ViewPager
    private lateinit var listView: ListView
    private lateinit var textView: TextView
    private var inputManager: InputMethodManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this
        if (mainViewModel == null) {
            mainViewModel = MainViewModel()

            mainViewModel!!.daos = Array(6) {
                Room.databaseBuilder(
                    mContext, ConversationDatabase::class.java,
                    mContext.resources.getString(labelText[it])
                ).allowMainThreadQueries().build().manager()
            }
        }

        getNewMessages(this)
        getContacts(this)

        setContentView(R.layout.activity_main)

        val tabs: TabLayout = findViewById(R.id.tabs)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        viewPager = findViewById(R.id.view_pager)

        fab = findViewById(R.id.fab)
        appBar = findViewById(R.id.appBarLayout)
        loading = findViewById(R.id.progress)
        backButton = findViewById(R.id.cancelSearch)
        textView = findViewById(R.id.info)
        searchLayout = findViewById(R.id.searchLayout)
        listView = findViewById(R.id.listView)
        searchEditText = findViewById(R.id.searchEditText)

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        setSupportActionBar(toolbar)

        viewPager.adapter = SectionsPagerAdapter(this, supportFragmentManager)
        viewPager.offscreenPageLimit = 3
        tabs.setupWithViewPager(viewPager)

        fab.setOnClickListener {
            startActivity(Intent(mContext, NewConversationActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                showSearchLayout()
            }
            R.id.action_spam -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", 4)
                startActivity(intent)
            }
            R.id.action_block -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", 5)
                startActivity(intent)
            }
        }
        return true
    }

    private fun showSearchLayout() {
        appBar.visibility = View.INVISIBLE
        searchLayout.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        searchEditText.requestFocus()
                        inputManager?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
                    }
                })
        }
        fab.visibility = View.GONE

        searchEditText.doOnTextChanged { _, _, _, _ ->
            val key = searchEditText.text.toString().trim().toLowerCase(Locale.ROOT)
            progress.visibility = View.VISIBLE
            val res = ArrayList<Conversation>()

            var recyclerViewState: Parcelable
            for (i in 0..3) {
                res.addAll(mainViewModel!!.daos[i].findBySender("%${key}%"))
                recyclerViewState = listView.onSaveInstanceState()!!
                listView.adapter = ConversationListViewAdaptor(mContext, res as List<Conversation>)
                listView.onRestoreInstanceState(recyclerViewState)
                listView.visibility = View.VISIBLE
                progress.visibility = View.GONE
            }
            listView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, i: Int, _: Long ->
                    val intent = Intent(mContext, ConversationActivity::class.java)
                    intent.putExtra("ye", res[i])
                    startActivity(intent)
                }

            textView.text = getString(R.string.no_matches)

            if (res.isEmpty()) textView.visibility = TextView.VISIBLE
            else textView.visibility = TextView.GONE
        }

        backButton.setOnClickListener{
            inputManager?.hideSoftInputFromWindow(it.windowToken, 0)
            Handler().postDelayed({
                appBar.apply {
                    visibility = View.VISIBLE
                    alpha = 0f
                    animate().alpha(1f).setDuration(300).start()
                }
                searchLayout.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            searchLayout.visibility = View.GONE
                            fab.visibility = View.VISIBLE
                            searchEditText.setText("")
                        }
                    }).start()
            }, 200)
        }
    }
}