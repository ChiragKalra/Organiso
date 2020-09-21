/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the prefsecific language governing permissions and
   limitations under the License.

*/

package com.bruhascended.sms

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.viewpager.widget.ViewPager
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.ConversationDatabase
import com.bruhascended.sms.services.SMSReceiver
import com.bruhascended.sms.ui.main.MainViewModel
import com.bruhascended.sms.ui.main.SectionsPagerAdapter
import com.bruhascended.sms.ui.mainViewModel
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {
    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var hiddenCategories: Array<Int>

    private lateinit var inputManager: InputMethodManager
    private var searchLayoutVisible = false
    private var promotionsVisible: Boolean = true

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

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean("dark_theme", false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)

        setContentView(R.layout.activity_main)

        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
            startActivityForResult(setSmsAppIntent, 1)
        }

        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        setSupportActionBar(mToolbar)

        if (prefs.getString("visible_categories", "null") == "null") {
            val vis = Array(4){it}
            val hid = Array(2){4+it}
            prefs.edit()
                .putString("visible_categories", Gson().toJson(vis))
                .putString("hidden_categories", Gson().toJson(hid))
                .apply()
        }

        val visibleCategories = Gson().fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )
        hiddenCategories = Gson().fromJson(
            prefs.getString("hidden_categories", ""), Array<Int>::class.java
        )
        viewPager.adapter = SectionsPagerAdapter(
            this, visibleCategories, prefs, supportFragmentManager
        )
        viewPager.offscreenPageLimit = 5
        viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                if (position != mainViewModel.selection.value) {
                    mainViewModel.selection.postValue(-1)
                }
            }
        })

        if (visibleCategories.size == 1) tabs.visibility = View.GONE
        else tabs.setupWithViewPager(viewPager)

        fab.setOnClickListener {
            startActivity(Intent(mContext, NewConversationActivity::class.java))
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onNewIntent(intent: Intent?) {}

    private var addedCategoriesToMenu: Boolean = false
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (addedCategoriesToMenu) return super.onPrepareOptionsMenu(menu)
        addedCategoriesToMenu = true
        for (i in hiddenCategories.indices) {
            val label = hiddenCategories[i]
            val customTitle = prefs.getString("custom_label_$label", "")!!
            menu.add(
                0, label, 400+i,
                if (customTitle=="") getString(labelText[label]) else customTitle
            )
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                startActivity(
                    Intent(mContext, SearchActivity::class.java)
                        .putExtra("type", "conversations")
                )
                overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
            }
            R.id.action_deep_search -> {
                startActivity(
                    Intent(mContext, SearchActivity::class.java)
                        .putExtra("type", "deep")
                )
                overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
            }
            R.id.action_settings -> {
                val intent = Intent(mContext, SettingsActivity::class.java)
                startActivity(intent)
            }
            else -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra("Type", item.itemId)
                startActivity(intent)
            }
        }
        return true
    }

    override fun onResume() {
        if (prefs.getBoolean("stateChanged", false)) {
            prefs.edit().putBoolean("stateChanged", false).apply()
            finish()
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        super.onResume()
    }
}