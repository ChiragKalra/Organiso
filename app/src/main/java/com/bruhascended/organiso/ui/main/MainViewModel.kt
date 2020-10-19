package com.bruhascended.organiso.ui.main

import android.Manifest
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bruhascended.core.data.SMSManager
import com.bruhascended.core.db.ContactsProvider
import com.bruhascended.organiso.R


class MainViewModel(mApp: Application) : AndroidViewModel(mApp) {

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

    var mContactsProvider: ContactsProvider = ContactsProvider(getApplication())
    var mSmsManager: SMSManager = SMSManager(getApplication())

    lateinit var visibleCategories: Array<Int>
    lateinit var hiddenCategories: Array<Int>


}