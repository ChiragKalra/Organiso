package com.bruhascended.organiso.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bruhascended.organiso.db.*
import kotlinx.coroutines.flow.Flow

class ConversationViewModel(mApp: Application) : AndroidViewModel(mApp) {
    private lateinit var mConversation: Conversation
    private lateinit var mPagingFlow: Flow<PagingData<Message>>
    private lateinit var mdb: MessageDao
    lateinit var messages: List<Message>

    var goToBottomVisible = false

    var conversation: Conversation
        get() = mConversation
        set(c) {
            mConversation = c
        }

    val sender: String
        get() = mConversation.sender

    var name: String?
        get() = mConversation.name
        set(value) {
            mConversation.name = value
        }

    val lastSms: String
        get() = mConversation.sender

    var isMuted: Boolean
        get() = mConversation.isMuted
        set(b) {
            mConversation.isMuted = b
        }

    val dao: MessageDao
        get() = mdb

    val pagingFlow
        get() = mPagingFlow

    fun loadLast() = mdb.loadLast()
    fun loadAll() = mdb.loadAll()

    fun init (conversation: Conversation) {
        if (::mConversation.isInitialized) return

        mConversation = conversation

        mdb = MessageDbFactory(getApplication()).of(sender).manager()

        mPagingFlow = Pager(
            PagingConfig(
                pageSize = 15,
                initialLoadSize = 15,
                prefetchDistance = 60,
                maxSize = 180,
            )
        ) {
            mdb.loadAllPaged()
        }.flow
    }
}