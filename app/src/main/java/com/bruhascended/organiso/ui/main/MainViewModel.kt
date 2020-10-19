package com.bruhascended.organiso.ui.main

import android.Manifest
import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.preference.PreferenceManager
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.db.ContactsProvider
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.MainDaoProvider
import com.bruhascended.organiso.R
import com.bruhascended.organiso.settings.CategorySettingsFragment.Companion.ARR_PREF_CUSTOM_LABELS
import com.bruhascended.organiso.settings.CategorySettingsFragment.Companion.PREF_HIDDEN_CATEGORIES
import com.bruhascended.organiso.settings.CategorySettingsFragment.Companion.PREF_VISIBLE_CATEGORIES
import com.bruhascended.organiso.settings.CategorySettingsFragment.Companion.toJson
import com.bruhascended.organiso.settings.CategorySettingsFragment.Companion.toLabelArray
import kotlinx.coroutines.flow.Flow


class MainViewModel(
    private val mApp: Application
) : AndroidViewModel(mApp) {

    companion object {
        val ARR_LABEL_STR = arrayOf (
            R.string.tab_text_1,
            R.string.tab_text_2,
            R.string.tab_text_3,
            R.string.tab_text_4,
            R.string.tab_text_5,
            R.string.tab_text_6,
        )

        val ARR_PERMS = arrayOf (
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }

    private lateinit var mMainDaoProvider: MainDaoProvider

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mApp)
    var mContactsProvider: ContactsProvider = ContactsProvider(mApp)
    var mSmsManager: SMSManager = SMSManager(mApp)

    lateinit var visibleCategories: Array<Int>
    lateinit var hiddenCategories: Array<Int>
    lateinit var customTabLabels: Array<String>
    lateinit var categoryFlows: Array<Flow<PagingData<Conversation>>>

    init {
        forceReload()
    }

    fun isDelayed(label: Int) = label in visibleCategories && label != visibleCategories.first()

    fun isEmpty(label: Int): Boolean {
        return mMainDaoProvider.getMainDaos()[label].loadSingle() == null
    }

    fun forceReload() {
        if (prefs.getString(PREF_VISIBLE_CATEGORIES, "") == "") {
            visibleCategories = Array(4) { it }
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
            if (c.isNullOrBlank()) mApp.getString(ARR_LABEL_STR[it]) else c
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