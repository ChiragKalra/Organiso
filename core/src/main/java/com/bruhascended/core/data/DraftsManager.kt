package com.bruhascended.core.data

import android.content.Context
import android.net.Uri
import com.bruhascended.core.constants.MESSAGE_TYPE_DRAFT
import com.bruhascended.core.constants.saveFile
import com.bruhascended.core.constants.saveSms
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDao

class DraftsManager(
    private val mContext: Context,
    private val mDao: MessageDao
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
    }

    fun delete(message: Message) {
        Thread {
            mDao.delete(mContext, message)
        }.start()
    }
}