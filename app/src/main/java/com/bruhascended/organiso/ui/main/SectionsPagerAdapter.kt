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

package com.bruhascended.organiso.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.bruhascended.organiso.data.SMSManager.Companion.ARR_LABEL_STR

class SectionsPagerAdapter(
        private val context: Context,
        private val visibleCategories: Array<Int>,
        private val prefs: SharedPreferences,
        fm: FragmentManager
        // TODO
    ): FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getItem(position: Int)
            = CategoryFragment.newInstance(visibleCategories[position], position)

    override fun getPageTitle(position: Int): String {
        val label = visibleCategories[position]
        val title = prefs.getString("custom_label_$label", "")
        return if (title.isNullOrEmpty()) context.getString(ARR_LABEL_STR[label]) else title
    }

    override fun getCount() = visibleCategories.size
}