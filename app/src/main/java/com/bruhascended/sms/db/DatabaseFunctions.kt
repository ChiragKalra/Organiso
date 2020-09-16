package com.bruhascended.sms.db

import android.content.Context
import androidx.room.Room
import com.bruhascended.db.Conversation
import com.bruhascended.db.MessageDatabase
import com.bruhascended.sms.ui.mainViewModel

fun moveTo(conversation: Conversation, to: Int, mContext: Context? = null) {
    Thread {
        mainViewModel.daos[conversation.label].delete(conversation)
        conversation.id = null
        if (to >= 0) {
            conversation.label = to
            conversation.forceLabel = to
            mainViewModel.daos[to].insert(conversation)
        } else {
            val mdb = Room.databaseBuilder(
                mContext!!, MessageDatabase::class.java, conversation.sender
            ).build().manager()
            mdb.nukeTable()
        }
    }.start()
}
