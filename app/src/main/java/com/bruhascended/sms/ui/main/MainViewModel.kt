package com.bruhascended.sms.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.db.ConversationDao


class MainViewModel : ViewModel() {
    lateinit var daos: Array<ConversationDao>

    var contacts = MutableLiveData<Array<Contact>?>().apply {
        postValue(null)
    }

    var selection = MutableLiveData<Boolean>().apply {
        postValue(false)
    }
}