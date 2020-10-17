package com.bruhascended.organiso.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.organiso.R
import com.bruhascended.core.data.SMSManager.Companion.LABEL_TRANSACTIONS
import com.bruhascended.core.data.SMSManager.Companion.MESSAGE_TYPE_INBOX
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.ml.getOtp
import com.bruhascended.core.db.MainDaoProvider

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

class GeneralFragment : PreferenceFragmentCompat() {

    companion object {
        const val PREF_DARK_THEME = "dark_theme"
        const val PREF_DELETE_OTP = "delete_otp"

        const val ARG_STATE_CHANGED = "stateChanged"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)

        val themePref: SwitchPreferenceCompat = findPreference(PREF_DARK_THEME)!!
        val deleteOtpPref: SwitchPreferenceCompat = findPreference(PREF_DELETE_OTP)!!
        val themeCategory: PreferenceCategory = findPreference("theme_category")!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            themeCategory.isVisible = false
            themePref.isVisible = false
        } else {
            themePref.setOnPreferenceChangeListener { _, _ ->
                val sp = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                sp.edit().putBoolean(ARG_STATE_CHANGED, true).apply()
                requireActivity().recreate()
                true
            }
        }

        val mContext = requireContext().applicationContext
        deleteOtpPref.setOnPreferenceChangeListener { _, state ->
            if (state as Boolean) {
                deleteOtpPref.setOnPreferenceChangeListener { _, _ ->  true}
                Thread {
                    for (con in MainDaoProvider(mContext).getMainDaos()[LABEL_TRANSACTIONS].loadAllSync()) {
                        MessageDbFactory(mContext).of(con.sender).apply {
                            manager().loadAllSync().forEach {
                                if (getOtp(it.text) != null && it.type== MESSAGE_TYPE_INBOX &&
                                    System.currentTimeMillis()-it.time > 15*60*1000) {
                                    manager().delete(it)
                                }
                            }
                            val it = manager().loadLastSync()
                            if (it == null) {
                                MainDaoProvider(mContext).getMainDaos()[2].delete(con)
                            } else {
                                if (con.lastSMS != it.text ||
                                    con.time != it.time ||
                                    con.lastMMS != (it.path != null)
                                ) {
                                    con.lastSMS = it.text
                                    con.time = it.time
                                    con.lastMMS = it.path != null
                                    MainDaoProvider(mContext).getMainDaos()[2].update(con)
                                }
                            }
                            close()
                        }
                    }
                }.start()
            }
            true
        }
    }
}