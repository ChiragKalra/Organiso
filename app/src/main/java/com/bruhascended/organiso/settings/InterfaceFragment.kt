package com.bruhascended.organiso.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.*
import com.bruhascended.organiso.R
import com.bruhascended.core.constants.*

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

class InterfaceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.interface_preferences, rootKey)

        val themePref: SwitchPreferenceCompat = findPreference(PREF_DARK_THEME)!!
        val themeCategory: PreferenceCategory = findPreference("theme_category")!!
        val swipePref: Preference = findPreference("swipe_header")!!


        swipePref.fragment = SwipeFragment::class.qualifiedName

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            themeCategory.isVisible = false
        } else {
            themePref.setOnPreferenceChangeListener { _, _ ->
                val sp = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                sp.edit().putBoolean(KEY_STATE_CHANGED, true).apply()
                requireActivity().recreate()
                true
            }
        }
    }
}