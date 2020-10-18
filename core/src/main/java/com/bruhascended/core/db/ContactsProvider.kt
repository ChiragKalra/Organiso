package com.bruhascended.core.db

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.core.data.ContactsManager

class ContactsProvider (mContext: Context) {

    companion object {
        const val KEY_LAST_REFRESH = "LAST_REFRESH"
    }

    private val mDb: ContactDatabase = Room.databaseBuilder(
        mContext, ContactDatabase::class.java, "contacts"
    ).allowMainThreadQueries().build()

    private val mCm = ContactsManager(mContext)
    private val mMainDaoProvider = MainDaoProvider(mContext)
    private val mPref = PreferenceManager.getDefaultSharedPreferences(mContext)

    private fun updateConversations (contacts: Array<Contact>) {
        contacts.forEach {
            for (i in 0..4) {
                val q =
                    mMainDaoProvider.getMainDaos()[i].findBySender(it.clean)
                if (q.isEmpty()) continue
                val res = q.first()
                if (res.name != it.name || res.address != it.address) {
                    res.name = it.name
                    res.address = it.address
                    mMainDaoProvider.getMainDaos()[i].update(res)
                }
                break
            }
        }
    }

    fun updateAsync() {
        if (System.currentTimeMillis() - mPref.getLong(KEY_LAST_REFRESH, 0) < 120*1000) return
        mPref.edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
        Thread {
            mDb.manager().apply {
                nukeTable()
                val contacts = mCm.getContactsList()
                insertAll(contacts)
                updateConversations(contacts)
            }
        }.start()
    }

    fun getSync(): Array<Contact> = mDb.manager().loadAllSync()

    fun getPaged(key: String) =
        mDb.manager().searchPaged("$key%", "% $key%")

    fun getNameOrNull(number: String) =
        mDb.manager().findByNumber(number)?.name

    fun close() {
        mDb.close()
    }
}