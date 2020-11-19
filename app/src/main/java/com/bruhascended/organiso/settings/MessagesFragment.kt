package com.bruhascended.organiso.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.core.constants.PREF_ALTERNATE_SIM
import com.bruhascended.core.constants.PREF_CONTACTS_ONLY
import com.bruhascended.core.constants.PREF_DELETE_OTP
import com.bruhascended.organiso.R
import com.bruhascended.organiso.services.OtpDeleteService
import com.bruhascended.organiso.services.PersonalMoveService

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
        val contactsOnlyPref: SwitchPreferenceCompat = findPreference(PREF_CONTACTS_ONLY)!!
        val simPref: SwitchPreferenceCompat = findPreference(PREF_ALTERNATE_SIM)!!
        val simPrefCategory: PreferenceCategory = findPreference("dual_sim_category")!!

        val mContext = requireContext()

        deleteOtpPref.setOnPreferenceChangeListener { _, v ->
            if (v == true) {
                AlertDialog.Builder(mContext)
                    .setTitle(getString(R.string.delete_all_otps_query))
                    .setPositiveButton(mContext.getString(R.string.ok)) { d, _ ->
                        mContext.startForegroundService(Intent(mContext, OtpDeleteService::class.java))
                        d.dismiss()
                    }.setNegativeButton(mContext.getString(R.string.cancel)) { d, _ ->
                        d.dismiss()
                    }.create().show()
            }
            true
        }

        contactsOnlyPref.setOnPreferenceChangeListener { _, v ->
            if (v == true) {
                mContext.startForegroundService(Intent(mContext, PersonalMoveService::class.java))
            }
            true
        }

        val sm = mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as SubscriptionManager

        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        simPrefCategory.isVisible = sm.activeSubscriptionInfoCount == 2

        //display sim phone numbers
        if (sm.activeSubscriptionInfoCount == 2) {
            simPref.summaryOn =
                "${sm.activeSubscriptionInfoList[1].number}(${getString(R.string.sim_2)})"
            simPref.summaryOff =
                "${sm.activeSubscriptionInfoList[0].number}(${getString(R.string.sim_1)})"
        }

    }
}