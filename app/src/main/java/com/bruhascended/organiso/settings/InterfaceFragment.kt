package com.bruhascended.organiso.settings

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.organiso.R

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

    companion object {
        const val PREF_DARK_THEME = "dark_theme"
        const val KEY_STATE_CHANGED = "stateChanged"

        fun AppCompatActivity.setPrefTheme() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            if (prefs.getBoolean(PREF_DARK_THEME, false)) setTheme(R.style.DarkTheme)
            else setTheme(R.style.LightTheme)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.interface_preferences, rootKey)

        val themePref: SwitchPreferenceCompat = findPreference(PREF_DARK_THEME)!!
        val themeCategory: PreferenceCategory = findPreference("theme_category")!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            themeCategory.isVisible = false
            themePref.isVisible = false
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