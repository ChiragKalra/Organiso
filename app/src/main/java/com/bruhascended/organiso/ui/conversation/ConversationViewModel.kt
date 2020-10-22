package com.bruhascended.organiso.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bruhascended.core.db.*
import kotlinx.coroutines.flow.Flow

class ConversationViewModel(mApp: Application) : AndroidViewModel(mApp) {
    private lateinit var mConversation: Conversation
    private lateinit var mPagingFlow: Flow<PagingData<Message>>
    private lateinit var mdb: MessageDatabase
    lateinit var messages: List<Message>


    var goToBottomVisible = false
    var extraIsVisible = false

    var conversation: Conversation
        get() = mConversation
        set(c) {
            mConversation = c
        }

    val sender: String
        get() = mConversation.clean

    var name: String?
        get() = mConversation.name
        set(value) {
            mConversation.name = value
        }

    val lastSms: String
        get() = mConversation.lastSMS

    var isMuted: Boolean
        get() = mConversation.isMuted
        set(b) {
            mConversation.isMuted = b
        }

    val dao
        get() = mdb.manager()

    val pagingFlow
        get() = mPagingFlow

    fun loadLast() = mdb.manager().loadLast()
    fun loadAll() = mdb.manager().loadAll()

    fun init (conversation: Conversation) {
        if (::mConversation.isInitialized) return

        mConversation = conversation

        mdb = MessageDbFactory(getApplication()).of(sender)

        mPagingFlow = Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 60,
                maxSize = 180,
            )
        ) {
            mdb.manager().loadAllPaged()
        }.flow
    }
}