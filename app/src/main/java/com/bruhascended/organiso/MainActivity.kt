package com.bruhascended.organiso

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.ui.main.CategoryPagerAdapter
import com.bruhascended.organiso.ui.main.MainViewModel
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayoutMediator
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

    private val onSearchCanceled = registerForActivityResult(StartActivityForResult()) {
        if (it.resultCode == RESULT_CANCELED) {
            appBarLayout?.postDelayed({
                appBarLayout.setExpanded(true, true)
            }, 300)
        }
    }

    private fun setupTabs() {
        if (mViewModel.visibleCategories.size == 1) {
            tabs.visibility = View.GONE
        } else {
            TabLayoutMediator(tabs, viewPager) { tab, position ->
                val label = mViewModel.visibleCategories[position]
                tab.text = mViewModel.customTabLabels[label]
            }.attach()
        }
    }

    private fun setupViewPager() {
        mViewModel.apply {
            viewPager.adapter = CategoryPagerAdapter(
                visibleCategories, supportFragmentManager, lifecycle
            )
            viewPager.offscreenPageLimit = 5
            viewPager.registerOnPageChangeCallback(object: OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    actionMode?.finish()
                    super.onPageSelected(position)
                }
            })
        }
        setupTabs()
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
            menu.add (
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

    override fun onStart() {
        super.onStart()
        mViewModel.mContactsProvider.updateAsync()
        if (PackageManager.PERMISSION_DENIED in
            Array(ARR_PERMS.size){ ActivityCompat.checkSelfPermission(this, ARR_PERMS[it])}
        ) {
            ActivityCompat.requestPermissions(this, ARR_PERMS, 1)
        }

        if (packageName != Telephony.Sms.getDefaultSmsPackage(this)) {
            mViewModel.mSmsManager.updateAsync()

            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(setSmsAppIntent)
        }

        viewPager.isUserInputEnabled = mViewModel.prefs.getBoolean(PREF_ACTION_NAVIGATE, true)

        if (mViewModel.prefs.getBoolean(KEY_STATE_CHANGED, false)) {
            mViewModel.prefs.edit().putBoolean(KEY_STATE_CHANGED, false).apply()
            finish()
            mViewModel.forceReload()
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
