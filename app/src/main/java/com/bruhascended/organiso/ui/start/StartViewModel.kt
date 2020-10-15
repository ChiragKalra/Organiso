package com.bruhascended.organiso.ui.start

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bruhascended.organiso.R

class StartViewModel : ViewModel() {

    val discStrings = arrayOf(
        R.string.getting_your_msgs,
        R.string.organising_your_msgs,
        R.string.done
    )

    val progress = MutableLiveData<Float>()
    val disc = MutableLiveData<Int>()
    val eta = MutableLiveData<Long>()

}