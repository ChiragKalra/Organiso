package com.bruhascended.sms.ui

import android.graphics.Bitmap
import com.bruhascended.sms.db.MessageDao
import com.bruhascended.sms.ui.main.MainViewModel
import java.util.HashMap


val dpMemoryCache = HashMap<String, Bitmap?>()

var conversationSender: String? = null
lateinit var conversationDao: MessageDao

lateinit var mainViewModel: MainViewModel
fun isMainViewModelNull() = !(::mainViewModel.isInitialized)
