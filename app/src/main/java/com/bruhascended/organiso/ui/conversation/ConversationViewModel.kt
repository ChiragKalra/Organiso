package com.bruhascended.organiso.ui.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.db.*
import kotlinx.coroutines.flow.Flow

class ConversationViewModel(mApp: Application) : AndroidViewModel(mApp) {
    lateinit var conversation: Conversation
    private lateinit var mPagingFlow: Flow<PagingData<Message>>
    private lateinit var mdb: MessageDatabase
    private lateinit var mContactsProvider: ContactsProvider
    lateinit var messages: List<Message>


    var goToBottomVisible = false
    var extraIsVisible = false

    val number: String
        get() = conversation.number

    val isBot: Boolean
        get() = conversation.isBot

    val name: String?
        get() = mContactsProvider.getNameOrNull(number)

    var isMuted: Boolean
        get() = conversation.isMuted
        set(b) {
            conversation.isMuted = b
        }

    val label: Int
        get() = conversation.label

    val dao
        get() = mdb.manager()

    val pagingFlow
        get() = mPagingFlow

    fun loadLast() = mdb.manager().loadLast()
    fun loadAll() = mdb.manager().loadAll()

    fun init (got: Conversation) {
        if (::conversation.isInitialized) return

        conversation = got
        mContactsProvider = ContactsProvider(getApplication())
        mdb = MessageDbFactory(getApplication()).of(number)

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