package com.bruhascended.organiso.ui.settings

import android.os.Build
import android.os.Bundle
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.organiso.R
import com.bruhascended.organiso.db.MessageDbProvider
import com.bruhascended.organiso.mainViewModel
import com.bruhascended.organiso.ml.getOtp
import com.bruhascended.organiso.requireMainViewModel

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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.general_preferences, rootKey)

        val themePref: SwitchPreferenceCompat = findPreference("dark_theme")!!
        val deleteOtpPref: SwitchPreferenceCompat = findPreference("delete_otp")!!
        val themeCategory: PreferenceCategory = findPreference("theme_category")!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            themeCategory.isVisible = false
            themePref.isVisible = false
        } else {
            themePref.setOnPreferenceChangeListener { _, _ ->
                val sp = PreferenceManager.getDefaultSharedPreferences(requireActivity())
                sp.edit().putBoolean("stateChanged", true).apply()
                requireActivity().recreate()
                true
            }
        }

        val mContext = requireContext().applicationContext
        deleteOtpPref.setOnPreferenceChangeListener { _, state ->
            if (state as Boolean) {
                deleteOtpPref.setOnPreferenceChangeListener { _, _ ->  true}
                Thread {
                    for (con in mainViewModel.daos[2].loadAllSync()) {
                        MessageDbProvider(mContext).of(con.sender).apply {
                            manager().loadAllSync().forEach {
                                if (getOtp(it.text) != null && it.type==1 &&
                                    System.currentTimeMillis()-it.time > 15*60*1000) {
                                    manager().delete(it)
                                }
                            }
                            val it = manager().loadLastSync()
                            if (it == null) {
                                requireMainViewModel(mContext)
                                mainViewModel.daos[2].delete(con)
                            } else {
                                if (con.lastSMS != it.text ||
                                    con.time != it.time ||
                                    con.lastMMS != (it.path != null)
                                ) {
                                    con.lastSMS = it.text
                                    con.time = it.time
                                    con.lastMMS = it.path != null
                                    mainViewModel.daos[2].update(con)
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