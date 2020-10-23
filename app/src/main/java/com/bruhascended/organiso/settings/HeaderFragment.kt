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

package com.bruhascended.organiso.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bruhascended.organiso.BuildConfig
import com.bruhascended.organiso.R

class HeaderFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.header_preferences, rootKey)
        val notifyPref: Preference = findPreference("notifications")!!
        val appBadgePref: Preference = findPreference("app_badge")!!
        val interfacePref: Preference = findPreference("interface")!!
        val messagesPref: Preference = findPreference("messages")!!
        val infoPref: Preference = findPreference("info")!!

        appBadgePref.summary = BuildConfig.VERSION_NAME

        interfacePref.fragment = InterfaceFragment::class.qualifiedName
        messagesPref.fragment = MessagesFragment::class.qualifiedName
        infoPref.fragment = InfoFragment::class.qualifiedName

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
    }
}