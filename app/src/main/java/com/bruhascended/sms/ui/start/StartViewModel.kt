package com.bruhascended.sms.ui.start

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.bruhascended.sms.db.Conversation

class StartViewModel : ViewModel() {

    val discStrings = arrayOf(
        "Getting your messages.",
        "Organising your messages.",
        "Finishing up."
    )

    val progress = MutableLiveData<Int>()
    val disc = MutableLiveData<Int>()

}