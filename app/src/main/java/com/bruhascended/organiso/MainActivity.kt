package com.bruhascended.organiso

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.bruhascended.organiso.db.ContactsProvider
import com.bruhascended.organiso.data.SMSManager.Companion.ARR_LABEL_STR
import com.bruhascended.organiso.ui.main.SectionsPagerAdapter
import com.bruhascended.organiso.ui.settings.GeneralFragment.Companion.PREF_DARK_THEME
import com.google.android.material.appbar.AppBarLayout
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*

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

class MainActivity : AppCompatActivity() {

    private lateinit var mContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var hiddenCategories: Array<Int>
    private lateinit var mContactsProvider: ContactsProvider
    private var actionMode: ActionMode? = null
    private var contactThread: Thread? = null

    private lateinit var inputManager: InputMethodManager
    private var searchLayoutVisible = false
    private var promotionsVisible: Boolean = true

    private val perms = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val onSearchCanceled = registerForActivityResult(StartActivityForResult()) {
        if (it.resultCode == RESULT_CANCELED) {
            appBarLayout?.postDelayed({
                appBarLayout.setExpanded(true, true)
            }, 300)
        }
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        actionMode = mode
        val trans: TransitionDrawable = appBarLayout.background as TransitionDrawable
        trans.startTransition(300)
        super.onSupportActionModeStarted(mode)
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        actionMode = null
        val trans: TransitionDrawable = appBarLayout.background as TransitionDrawable
        trans.reverseTransition(150)
        super.onSupportActionModeFinished(mode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(PREF_DARK_THEME, false)) setTheme(R.style.DarkTheme)
        else setTheme(R.style.LightTheme)

        setContentView(R.layout.activity_main)

        mContext = this
        mContactsProvider = ContactsProvider(this)

        if (PackageManager.PERMISSION_DENIED in
            Array(perms.size){ ActivityCompat.checkSelfPermission(this, perms[it])})
            ActivityCompat.requestPermissions(this, perms, 1)

        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, mContext.packageName)
            startActivity(setSmsAppIntent)
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
                actionMode?.finish()
            }
        })

        if (visibleCategories.size == 1) tabs.visibility = View.GONE
        else tabs.setupWithViewPager(viewPager)

        fab.setOnClickListener {
            startActivity(Intent(mContext, NewConversationActivity::class.java))
        }
    }

    private var addedCategoriesToMenu: Boolean = false
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (addedCategoriesToMenu) return super.onPrepareOptionsMenu(menu)
        addedCategoriesToMenu = true
        for (i in hiddenCategories.indices) {
            val label = hiddenCategories[i]
            val customTitle = prefs.getString("custom_label_$label", "")!!
            menu.add(
                0, label, 400 + i,
                if (customTitle == "") getString(ARR_LABEL_STR[label]) else customTitle
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
                appBarLayout.apply {
                    val params = appBarLayout.layoutParams as CoordinatorLayout.LayoutParams
                    val behavior = params.behavior as AppBarLayout.Behavior
                    ValueAnimator.ofInt().apply {
                        interpolator = DecelerateInterpolator()
                        duration = 300
                        setIntValues(behavior.topAndBottomOffset, -tabs.height)
                        addUpdateListener { animation ->
                            behavior.topAndBottomOffset = animation.animatedValue as Int
                            requestLayout()
                        }
                        start()
                    }
                    onSearchCanceled.launch(Intent(mContext, SearchActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
                }
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
        mContactsProvider.updateAsync()
        if (prefs.getBoolean("stateChanged", false)) {
            prefs.edit().putBoolean("stateChanged", false).apply()
            finish()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        super.onResume()
    }

    override fun onDestroy() {
        mContactsProvider.close()
        super.onDestroy()
    }
}
