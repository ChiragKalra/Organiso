package com.bruhascended.sms.ui.main

import android.content.Context
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.bruhascended.sms.R

class SectionsPagerAdapter(
        private val context: Context,
        fm: FragmentManager,
        private val promo: Boolean
    ): FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    private val tabTitles = arrayOf(
        R.string.tab_text_1,
        R.string.tab_text_2,
        R.string.tab_text_3,
        R.string.tab_text_4,
        R.string.tab_text_5,
        R.string.tab_text_6
    )

    override fun getItem(position: Int) = CategoryFragment.newInstance(position)

    override fun getPageTitle(position: Int) = context.resources.getString(tabTitles[position])

    override fun getCount() = if (promo) 4 else 3
}