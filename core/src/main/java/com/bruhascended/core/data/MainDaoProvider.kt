package com.bruhascended.core.data

import android.content.Context
import com.bruhascended.core.db.ConversationDao
import com.bruhascended.core.db.ConversationDbFactory

class MainDaoProvider(private val mContext: Context) {
    companion object {
        private var mDaos: Array<ConversationDao>? = null
    }

    fun getMainDaos(): Array<ConversationDao> {
        return mDaos ?: synchronized(this) {
            mDaos ?: Array(6) {
                ConversationDbFactory(mContext).of(it).manager()
            }.also { mDaos = it }
        }
    }
}