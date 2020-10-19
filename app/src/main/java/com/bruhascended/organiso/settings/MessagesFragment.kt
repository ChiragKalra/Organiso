package com.bruhascended.organiso.settings

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

class MessagesFragment : PreferenceFragmentCompat() {

    companion object {
        const val PREF_DELETE_OTP = "delete_otp"
        const val PREF_COPY_OTP = "copy_otp"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.messages_preferences, rootKey)

        val deleteOtpPref: SwitchPreferenceCompat = findPreference(PREF_DELETE_OTP)!!

        val mContext = requireContext().applicationContext
        deleteOtpPref.setOnPreferenceChangeListener { _, state ->
            if (state as Boolean) {
                deleteOtpPref.setOnPreferenceChangeListener { _, _ ->  true}
                Thread {
                    for (con in MainDaoProvider(mContext).getMainDaos()[LABEL_TRANSACTIONS].loadAllSync()) {
                        MessageDbFactory(mContext).of(con.clean).apply {
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