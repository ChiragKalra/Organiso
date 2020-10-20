package com.bruhascended.organiso.settings

import android.os.Bundle
import androidx.preference.*
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.RadioButtonPreference
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

class SwipeFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.swipe_preferences, rootKey)

        val nonePref: RadioButtonPreference = findPreference("action_null")!!
        val navPref: RadioButtonPreference = findPreference(PREF_ACTION_NAVIGATE)!!
        val customPref: RadioButtonPreference = findPreference(PREF_ACTION_CUSTOM)!!
        val radioGroup = arrayOf(nonePref, navPref, customPref)

        val customActionCategory: PreferenceCategory = findPreference("action_category")!!
        val leftPref: ListPreference = findPreference(PREF_CUSTOM_LEFT)!!
        val rightPref: ListPreference = findPreference(PREF_CUSTOM_RIGHT)!!

        leftPref.title = requireContext().getString(R.string.choose_left_swipe) +
                ": " + leftPref.value
        rightPref.title = requireContext().getString(R.string.choose_left_swipe) +
                ": " + rightPref.value

        leftPref.setOnPreferenceChangeListener { _, _ ->
            leftPref.title = requireContext().getString(R.string.choose_left_swipe) +
                ": " + leftPref.value
            true
        }
        rightPref.setOnPreferenceChangeListener { _, _ ->
            rightPref.title = requireContext().getString(R.string.choose_right_swipe) +
                    ": " + rightPref.value
            true
        }

        customActionCategory.isVisible = customPref.isChecked

        val listener = { pref: Preference ->
            radioGroup.forEach {
                if (it !== pref) {
                    it.isChecked = false
                }
            }
            customActionCategory.isVisible = pref === customPref
            true
        }
        radioGroup.forEach{
            it.setOnPreferenceClickListener { pref ->
                listener(pref)
            }
        }
    }

    override fun onDestroy() {
        requireActivity().title = getString(R.string.interface_settings)
        super.onDestroy()
    }
}