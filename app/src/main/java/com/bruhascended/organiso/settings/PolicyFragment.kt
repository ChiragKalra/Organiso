package com.bruhascended.organiso.settings

import android.os.Bundle
import android.text.SpannableString
import androidx.core.text.HtmlCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bruhascended.organiso.R
import org.apache.commons.io.IOUtils

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

open class PolicyFragment (
    private val filePath: String
) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.license_preferences, rootKey)
        val licensePref: Preference = findPreference("license_text")!!
        val inputStream = resources.assets.open(filePath)
        licensePref.title = SpannableString(
            HtmlCompat.fromHtml(
                IOUtils.toString(inputStream, "UTF-8"),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        )
    }

    override fun onDestroy() {
        requireActivity().title = getString(R.string.info)
        super.onDestroy()
    }
}

class LicenseFragment : PolicyFragment("license.html")
class PrivacyFragment : PolicyFragment("privacy_policy.html")
class TermsAndConditionsFragment : PolicyFragment("terms_and_conditions.html")
