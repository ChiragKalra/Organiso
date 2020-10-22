package com.bruhascended.core.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.bruhascended.core.constants.MESSAGE_TYPE_DRAFT
import com.bruhascended.core.constants.saveFile
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageDao

class DraftsManager(
    private val mContext: Context,
    private val mDao: MessageDao
) {

    fun create(message: String, address: String, data: Uri?) {
        val date = System.currentTimeMillis()
        mDao.insert(Message(
            message,
            MESSAGE_TYPE_DRAFT,
            date,
            path = data?.saveFile(mContext, date.toString())
        ))
        try {
            val values = ContentValues()
            values.put("address", address)
            values.put("body", message)
            values.put("read", 1)
            values.put("date", date)
            values.put("type", MESSAGE_TYPE_DRAFT)
            mContext.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (ex: Exception) { }
    }

    fun delete(message: Message, address: String) {
        Thread {
            mDao.delete(mContext, message, address)
        }.start()
    }
}