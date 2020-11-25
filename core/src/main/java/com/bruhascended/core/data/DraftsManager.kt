package com.bruhascended.core.data

import android.content.Context
import android.net.Uri
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDao

class DraftsManager(
    private val mContext: Context,
    private val mDao: MessageDao,
    private val mConversation: Conversation
) {

    fun create(message: String, address: String, data: Uri?) {
        val date = System.currentTimeMillis()
        val id = mContext.saveSms(address, message, MESSAGE_TYPE_DRAFT)
        mDao.insert(Message(
            message,
            MESSAGE_TYPE_DRAFT,
            date,
            path = mContext.saveFile(data, date.toString()),
            id = id,
        ))

        // update conversation after draft is added
        if (mConversation.label == LABEL_NONE) {
            // insert conversation to personal db if first message
            mConversation.time = System.currentTimeMillis()
            MainDaoProvider(mContext).getMainDaos()[LABEL_PERSONAL].insert(mConversation)
        } else {
            // update time if it already exists on db
            MainDaoProvider(mContext).getMainDaos()[mConversation.label].updateTime(
                mConversation.number,
                System.currentTimeMillis()
            )
        }
    }

    fun delete(message: Message) {
        Thread {
            mDao.delete(mContext, message)
        }.start()
    }
}