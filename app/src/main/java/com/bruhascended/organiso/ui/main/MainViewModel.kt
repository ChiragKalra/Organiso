package com.bruhascended.organiso.ui.main

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.R
import kotlinx.coroutines.flow.Flow

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

class MainViewModel(
    private val mApp: Application
) : AndroidViewModel(mApp) {

    private lateinit var mMainDaoProvider: MainDaoProvider

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApp)
    var mContactsProvider: ContactsProvider = ContactsProvider(mApp)
    var mSmsManager: SMSManager = SMSManager(mApp)

    lateinit var visibleCategories: Array<Int>
    lateinit var hiddenCategories: Array<Int>
    lateinit var customTabLabels: Array<String>
    lateinit var categoryFlows: Array<Flow<PagingData<Conversation>>>

    var goToTop = Array(5){{}}

    init {
        forceReload()
    }

    fun isDelayed(label: Int) = label in visibleCategories && label != visibleCategories.first()

    fun isEmpty(label: Int) =
        mMainDaoProvider.getMainDaos()[label].loadSingle() == null

    fun getLiveUnreadCount(label: Int) =
        mMainDaoProvider.getMainDaos()[label].loadLiveUnreadCount()


    fun forceReload() {
        if (prefs.getString(PREF_VISIBLE_CATEGORIES, "") == "") {
            visibleCategories = Array(4) {
                when(it) {
                    LABEL_PERSONAL -> LABEL_IMPORTANT
                    LABEL_IMPORTANT -> LABEL_PERSONAL
                    else -> it
                }
            }
            hiddenCategories = Array(2) { 4 + it }
            prefs.edit()
                .putString(PREF_VISIBLE_CATEGORIES, visibleCategories.toJson())
                .putString(PREF_HIDDEN_CATEGORIES, hiddenCategories.toJson())
                .apply()
        } else {
            visibleCategories =
                prefs.getString(PREF_VISIBLE_CATEGORIES, "").toLabelArray()
            hiddenCategories =
                prefs.getString(PREF_HIDDEN_CATEGORIES, "").toLabelArray()
        }

        customTabLabels = Array(6) {
            val c = prefs.getString(ARR_PREF_CUSTOM_LABELS[it], "")
            val labelArr = mApp.resources.getStringArray(R.array.labels)
            if (c.isNullOrBlank()) labelArr[it] else c
        }

        mMainDaoProvider = MainDaoProvider(mApp)

        categoryFlows = Array(6) {
            Pager(
                PagingConfig(
                    pageSize = 12,
                    initialLoadSize = 12,
                    prefetchDistance = 12,
                    maxSize = 180,
                )
            ) {
                mMainDaoProvider.getMainDaos()[it].loadAllPaged()
            }.flow
        }

    }

}