package com.bruhascended.sms.ui.start

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class StartViewModel : ViewModel() {

    val discStrings = arrayOf(
        "Getting your messages.",
        "Organising your messages.",
        "Finishing up."
    )

    val progress = MutableLiveData<Int>()
    val disc = MutableLiveData<Int>()
    val eta = MutableLiveData<Long>()

}