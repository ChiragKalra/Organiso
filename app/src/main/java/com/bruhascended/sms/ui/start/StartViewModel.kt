package com.bruhascended.sms.ui.start

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class StartViewModel : ViewModel() {

    val discStrings = arrayOf(
        "Getting your messages.",
        "Organising your messages.",
        "Done."
    )

    val progress = MutableLiveData<Float>()
    val disc = MutableLiveData<Int>()
    val eta = MutableLiveData<Long>()

}