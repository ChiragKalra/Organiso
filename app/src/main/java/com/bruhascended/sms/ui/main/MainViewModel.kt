package com.bruhascended.sms.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.db.ConversationDao


class MainViewModel : ViewModel() {
    var daos: Array<ConversationDao>? = null

    var contacts = MutableLiveData<Array<Contact>?>().apply {
        postValue(null)
    }

    var selection = MutableLiveData<Int>().apply {
        postValue(-1)
    }
}