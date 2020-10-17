package com.bruhascended.organiso.db

import android.content.Context

class MainDaoProvider(private val mContext: Context) {
    companion object {
        private var mDaos: Array<ConversationDao>? = null
    }

    fun getMainDaos(): Array<ConversationDao> {
        if (mDaos == null) {
            mDaos = Array(6) {
                ConversationDbFactory(mContext).of(it).manager()
            }
        }
        return mDaos!!
    }
}