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

package com.bruhascended.sms

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.services.SMSReceiver
import com.bruhascended.sms.ui.main.ConversationListViewAdaptor
import com.bruhascended.sms.ui.main.MainViewModel
import com.bruhascended.sms.ui.main.SectionsPagerAdapter
import com.bruhascended.sms.ui.mainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList



class MainActivity : AppCompatActivity() {
    private lateinit var mContext: Context

    private var searchLayoutVisible = false
    private var inputManager: InputMethodManager? = null
    private var promotionsVisible: Boolean = true


    private fun showSearchLayout() {
        searchLayoutVisible = true
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
                res.addAll(mainViewModel.daos[i].findBySender("%${key}%"))
                recyclerViewState = searchListView.onSaveInstanceState()!!
                searchListView.adapter = ConversationListViewAdaptor(mContext, res as List<Conversation>)
                searchListView.onRestoreInstanceState(recyclerViewState)
                searchListView.visibility = View.VISIBLE
                progress.visibility = View.GONE
            }
            searchListView.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, i: Int, _: Long ->
                    val intent = Intent(mContext, ConversationActivity::class.java)
                    intent.putExtra("ye", res[i])
                    startActivity(intent)
                }

            info.text = getString(R.string.no_matches)

            if (res.isEmpty()) info.visibility = TextView.VISIBLE
            else info.visibility = TextView.GONE
        }
    }

    private fun hideSearchLayout() {
        searchLayoutVisible = false
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
                    inputManager?.hideSoftInputFromWindow(mBackButton.windowToken, 0)
                    searchLayout.visibility = View.GONE
                    GlobalScope.launch {
                        delay(300)
                        runOnUiThread{
                            fab.visibility = View.VISIBLE
                        }
                    }
                    searchEditText.setText("")
                }
            }).start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startService(Intent(this, SMSReceiver::class.java))

        mContext = this
        mainViewModel = MainViewModel()

        mainViewModel.daos = Array(6) {
            Room.databaseBuilder(
                mContext, ConversationDatabase::class.java,
                mContext.resources.getString(labelText[it])
            ).allowMainThreadQueries().build().manager()
        }

        Thread {
            mainViewModel.contacts.postValue(ContactsManager(mContext).getContactsList())
        }.start()

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getBoolean("dark_theme", false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)

        setContentView(R.layout.activity_main)

        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
            startActivityForResult(setSmsAppIntent, 1)
        }

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

        setSupportActionBar(mToolbar)

        promotionsVisible = sp.getBoolean("promotions_category_visible", true)
        viewPager.adapter = SectionsPagerAdapter(this, supportFragmentManager, promotionsVisible)
        viewPager.offscreenPageLimit = if (promotionsVisible) 3 else 2
        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position != mainViewModel.selection.value) {
                    mainViewModel.selection.postValue(-1)
                }
            }
        })
        tabs.setupWithViewPager(viewPager)

        fab.setOnClickListener {
            startActivity(Intent(mContext, NewConversationActivity::class.java))
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent?) {}

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        promotionsVisible = sp.getBoolean("promotions_category_visible", true)
        if (promotionsVisible) menu?.removeItem(R.id.action_promotions)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                showSearchLayout()
                mBackButton.setOnClickListener{ hideSearchLayout() }
            }
            R.id.action_spam -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", 4)
                startActivity(intent)
            }
            R.id.action_promotions -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", 3)
                startActivity(intent)
            }
            R.id.action_block -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", 5)
                startActivity(intent)
            }
            R.id.action_settings -> {
                val intent = Intent(mContext, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
        return true
    }

    override fun onBackPressed() {
        if (searchLayoutVisible) hideSearchLayout()
        else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        if (sp.getBoolean("stateChanged", false)) {
            sp.edit().putBoolean("stateChanged", false).apply()
            recreate()
        }
    }
}