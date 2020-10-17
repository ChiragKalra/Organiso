package com.bruhascended.organiso.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bruhascended.organiso.db.Contact
import com.bruhascended.organiso.db.ConversationDao


class MainViewModel : ViewModel() {
    lateinit var daos: Array<ConversationDao>

    var contacts = MutableLiveData<Array<Contact>?>().apply {
        postValue(null)
    }
}