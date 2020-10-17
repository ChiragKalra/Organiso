package com.bruhascended.organiso.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bruhascended.core.db.Contact
import com.bruhascended.core.db.ConversationDao


class MainViewModel : ViewModel() {
    lateinit var daos: Array<ConversationDao>

    var contacts = MutableLiveData<Array<Contact>?>().apply {
        postValue(null)
    }
}