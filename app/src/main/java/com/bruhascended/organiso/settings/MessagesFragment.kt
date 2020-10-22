package com.bruhascended.organiso.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.core.constants.PREF_DELETE_OTP
import com.bruhascended.organiso.R
import com.bruhascended.organiso.services.OtpDeleteService

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

class MessagesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.messages_preferences, rootKey)

        val deleteOtpPref: SwitchPreferenceCompat = findPreference(PREF_DELETE_OTP)!!

        val mContext = requireContext()


        deleteOtpPref.setOnPreferenceChangeListener { _, v ->
            if (v == true) {
                AlertDialog.Builder(mContext)
                    .setTitle("Delete all previous OTPs?")
                    .setPositiveButton(mContext.getString(R.string.ok)) { d, _ ->
                        mContext.startService(Intent(mContext, OtpDeleteService::class.java))
                        d.dismiss()
                    }.setNegativeButton(mContext.getString(R.string.cancel)) { d, _ ->
                        d.dismiss()
                    }.create().show()
            }
            true
        }
    }
}