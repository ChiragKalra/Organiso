package com.bruhascended.sms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.ui.main.MainViewModel
import com.bruhascended.sms.ui.main.SectionsPagerAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

var mainViewModel: MainViewModel? = null

fun moveTo(conversation: Conversation, to: Int) {
    Thread(Runnable {
        mainViewModel!!.daos[conversation.label].delete(conversation)

        conversation.id = null
        conversation.label = to
        mainViewModel!!.daos[to].insert(conversation)
    }).start()
}


class MainActivity : AppCompatActivity() {
    private lateinit var mContext: Context

    private fun getNewMessages() {
        Thread(Runnable {
            val manager = SMSManager(mContext)
            manager.getMessages()
            manager.getLabels(null)
            manager.saveMessages()
        }).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getNewMessages()

        mContext = this

        mainViewModel = MainViewModel()
        mainViewModel!!.daos = Array(6){
            Room.databaseBuilder(
                mContext, ConversationDatabase::class.java,
                mContext.resources.getString(labelText[it])
            ).build().manager()
        }

        val sectionsPagerAdapter = SectionsPagerAdapter(this, supportFragmentManager, mainViewModel!!)
        val viewPager: ViewPager = findViewById(R.id.view_pager)
        viewPager.adapter = sectionsPagerAdapter
        viewPager.offscreenPageLimit = 3
        val tabs: TabLayout = findViewById(R.id.tabs)
        tabs.setupWithViewPager(viewPager)
        val fab: FloatingActionButton = findViewById(R.id.fab)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
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
}