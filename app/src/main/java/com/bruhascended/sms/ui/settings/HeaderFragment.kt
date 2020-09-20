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

package com.bruhascended.sms.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.sms.BuildConfig
import com.bruhascended.sms.R

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.header_preferences, rootKey)
        val notifyPref: Preference = findPreference("notifications")!!
        val themePref: SwitchPreferenceCompat = findPreference("dark_theme")!!
        val appBadgePref: Preference = findPreference("app_badge")!!

        appBadgePref.summary = "v${BuildConfig.VERSION_NAME}"

        notifyPref.setOnPreferenceClickListener {
            val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                putExtra(
                    "android.provider.extra.APP_PACKAGE",
                    requireActivity().packageName
                )
            }
            requireActivity().startActivity(intent)
            false
        }
        themePref.setOnPreferenceChangeListener { _, _ ->
            val sp = PreferenceManager.getDefaultSharedPreferences(requireActivity())
            sp.edit().putBoolean("stateChanged", true).apply()
            requireActivity().recreate()
            true
        }
    }
}