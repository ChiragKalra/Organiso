package com.bruhascended.organiso

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Telephony
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.view.get
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bruhascended.core.constants.ARR_PERMS
import com.bruhascended.core.constants.EXTRA_LABEL
import com.bruhascended.core.constants.KEY_STATE_CHANGED
import com.bruhascended.core.constants.PREF_ACTION_NAVIGATE
import com.bruhascended.organiso.common.requestDefaultApp
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.ui.main.CategoryPagerAdapter
import com.bruhascended.organiso.ui.main.MainViewModel
import com.google.android.material.bottomnavigation.LabelVisibilityMode
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
    private val mViewModel: MainViewModel by viewModels()

    private lateinit var mContext: Context
    private lateinit var inputManager: InputMethodManager

    private val onSearchCanceled = registerForActivityResult(StartActivityForResult()) {}

    private val onDefaultAppResult = registerForActivityResult(StartActivityForResult()) {
        if (PackageManager.PERMISSION_DENIED in
            Array(ARR_PERMS.size){ ActivityCompat.checkSelfPermission(this, ARR_PERMS[it]) }
        ) {
            Toast.makeText(this, getString(R.string.insufficient_permissions), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("ResourceType")
    private fun setupBottomNav() {
        if (mViewModel.visibleCategories.size == 1) {
            bottom.visibility = View.GONE
        } else {
            mViewModel.visibleCategories.forEachIndexed { i, label ->
                bottom.menu.add(
                    0, i, 400 + i,
                    mViewModel.customTabLabels[label]
                )
                val res = resources.obtainTypedArray(R.array.category_icons)
                bottom.menu[i].setIcon(res.getResourceId(label, 0))
                res.recycle()

                val badge = bottom.getOrCreateBadge(i)
                badge.isVisible = false
                mViewModel.getLiveUnreadCount(mViewModel.visibleCategories[i]).observe(this, {
                    badge.isVisible = it > 0
                    badge.number = it
                })
            }
            val typedArray = obtainStyledAttributes(
                intArrayOf(R.attr.navActiveColor, R.attr.navInactiveColor)
            )
            val colors = intArrayOf(
                typedArray.getColor(0, 0),
                typedArray.getColor(1, 0)
            )
            typedArray.recycle()
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            bottom.itemIconTintList = ColorStateList(states, colors)
            bottom.labelVisibilityMode = LabelVisibilityMode.LABEL_VISIBILITY_SELECTED
            bottom.setOnNavigationItemSelectedListener {
                viewPager.setCurrentItem(
                    it.itemId, mViewModel.prefs.getBoolean(PREF_ACTION_NAVIGATE, true)
                )
                true
            }
            bottom.setOnNavigationItemReselectedListener {
                mViewModel.goToTop[mViewModel.visibleCategories[it.itemId]]()
            }
        }
    }

    private fun setupViewPager() {
        setupBottomNav()
        mViewModel.apply {
            viewPager.adapter = CategoryPagerAdapter(
                visibleCategories, supportFragmentManager, lifecycle
            )
            viewPager.offscreenPageLimit = 4
            viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    actionMode?.finish()
                    bottom.selectedItemId = position
                    super.onPageSelected(position)
                }
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setPrefTheme()
        setContentView(R.layout.activity_main)
        setSupportActionBar(mToolbar)

        mContext = this
        inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        setupViewPager()
        fab.setOnClickListener {
            startActivity(Intent(this, NewConversationActivity::class.java))
        }
    }

    private var addedCategoriesToMenu: Boolean = false

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (addedCategoriesToMenu) return super.onPrepareOptionsMenu(menu)
        addedCategoriesToMenu = true
        for (i in mViewModel.hiddenCategories.indices) {
            val label = mViewModel.hiddenCategories[i]
            menu.add(
                0, label, 400 + i,
                mViewModel.customTabLabels[label]
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
                    onSearchCanceled.launch(Intent(mContext, SearchActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, R.anim.hold)
                }
            }
            R.id.action_settings -> {
                startActivity(Intent(mContext, SettingsActivity::class.java))
            }
            R.id.action_saved -> {
                startActivity(Intent(mContext, SavedActivity::class.java))
            }
            R.id.action_scheduled -> {
                startActivity(Intent(mContext, ScheduledActivity::class.java))
            }
            else -> {
                val intent = Intent(mContext, ExtraCategoryActivity::class.java)
                intent.putExtra(EXTRA_LABEL, item.itemId)
                startActivity(intent)
            }
        }
        return true
    }

    private var actionMode: ActionMode? = null

    override fun onSupportActionModeStarted(mode: ActionMode) {
        actionMode = mode
        super.onSupportActionModeStarted(mode)
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        actionMode = null
        super.onSupportActionModeFinished(mode)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.mContactsProvider.updateAsync()

        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            mViewModel.mSmsManager.updateAsync()
            requestDefaultApp(onDefaultAppResult)
        }

        viewPager.isUserInputEnabled = mViewModel.prefs.getBoolean(PREF_ACTION_NAVIGATE, true)
        mViewModel.visibleCategories.forEachIndexed { i, _ ->
            val badge = bottom.getOrCreateBadge(i)
            badge.isVisible = badge.number > 0
        }

        if (mViewModel.prefs.getBoolean(KEY_STATE_CHANGED, false)) {
            mViewModel.prefs.edit().putBoolean(KEY_STATE_CHANGED, false).apply()
            finish()
            mViewModel.forceReload()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
