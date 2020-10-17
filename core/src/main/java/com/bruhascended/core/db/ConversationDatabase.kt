package com.bruhascended.core.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.*
import com.bruhascended.core.data.SMSManager.Companion.ARR_LABEL_STR
import com.google.gson.Gson
import java.io.Serializable

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

*/

@Entity(tableName = "conversations")
data class Conversation (
    val sender: String,
    var name: String? = null,
    @PrimaryKey(autoGenerate = true)
    var id: Long? = null,
    var label: Int = 0,
    var forceLabel: Int = -1,
    var probabilities: FloatArray = FloatArray(5) { if (it == 0) 1F else 0F },
    var read: Boolean = true,
    var time: Long = 0,
    var lastSMS: String = "",
    var isMuted: Boolean = false,
    var lastMMS: Boolean = false
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Conversation
        if (sender != other.sender) return false
        if (name != other.name) return false
        if (read != other.read) return false
        if (time != other.time) return false
        if (lastSMS != other.lastSMS) return false
        if (lastMMS != other.lastMMS) return false
        if (isMuted != other.isMuted) return false
        return true
    }

    override fun hashCode(): Int {
        return sender.hashCode()
    }
}

@Dao
interface ConversationDao {
    @Insert
    fun insert(conversation: Conversation)

    @Update
    fun update(conversation: Conversation)

    @Delete
    fun delete(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE sender LIKE :sender ORDER BY time DESC")
    fun findBySender(sender: String): List<Conversation>

    @Query("SELECT * FROM conversations WHERE LOWER(name) LIKE :key OR LOWER(name) LIKE :altKey OR sender LIKE :key ORDER BY time DESC")
    fun search(key: String, altKey: String=""): List<Conversation>

    @Query("SELECT * FROM conversations LIMIT 1")
    fun loadSingle(): Conversation?

    @Query("SELECT * FROM conversations ORDER BY time DESC")
    fun loadAll(): LiveData<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY time DESC")
    fun loadAllPaged(): PagingSource<Int, Conversation>

    @Query("SELECT * FROM conversations")
    fun loadAllSync(): List<Conversation>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Conversation>

}

object ConversationComparator : DiffUtil.ItemCallback<Conversation>() {
    override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation) =
        oldItem == newItem
}

class Converters {
    @TypeConverter
    fun listToJson(value: FloatArray?): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToList(value: String?): FloatArray = Gson().fromJson(value, FloatArray::class.java)
}

@Database(entities = [Conversation::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun manager(): ConversationDao
}

class ConversationDbFactory (private val mContext: Context) {
    fun of (label: Int, mainThread: Boolean = true) = Room.databaseBuilder(
        mContext, ConversationDatabase::class.java, ARR_LABEL_STR[label]
    ).apply {
        if (mainThread) allowMainThreadQueries()
    }.build()
}


fun Conversation.moveTo(to: Int, mContext: Context) {
    MainDaoProvider(mContext).getMainDaos()[label].delete(this)
    id = null
    if (to >= 0) {
        label = to
        forceLabel = to
        MainDaoProvider(mContext).getMainDaos()[to].insert(this)
    } else MessageDbFactory(mContext).of(sender).apply {
        manager().nukeTable()
        close()
    }
}