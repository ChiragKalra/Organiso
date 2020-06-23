package com.bruhascended.sms.ui.main

import androidx.lifecycle.ViewModel
import com.bruhascended.sms.db.ConversationDao


class MainViewModel : ViewModel() {
    lateinit var daos: Array<ConversationDao>
}