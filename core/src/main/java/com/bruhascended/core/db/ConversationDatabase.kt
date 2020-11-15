package com.bruhascended.core.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import com.bruhascended.core.constants.LABEL_NONE
import com.bruhascended.core.constants.LABEL_PERSONAL
import com.bruhascended.core.data.MainDaoProvider
import com.google.gson.Gson
import java.io.Serializable
import kotlin.math.abs

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
data class Conversation(
    @PrimaryKey
    val number: String,
    var time: Long = 0,
    var label: Int = LABEL_PERSONAL,
    var forceLabel: Int = -1,
    var probabilities: Array<Float> =
        Array(5) { if (it == LABEL_PERSONAL) 1F else 0F },
    var read: Boolean = true,
    var isMuted: Boolean = false
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Conversation
        if (read != other.read) return false
        if (isMuted != other.isMuted) return false
        if (time != other.time) return false
        return true
    }

    override fun hashCode(): Int {
        return number.hashCode()
    }

    override fun toString(): String =
        Gson().toJson(this)

    val isBot
        get() = number.first().isLetter()

    val id
        get() = abs(hashCode())

    fun moveTo(to: Int, mContext: Context) {
        MainDaoProvider(mContext).getMainDaos()[label].delete(this)
        if (to != LABEL_NONE) {
            label = to
            forceLabel = to
            MainDaoProvider(mContext).getMainDaos()[to].insert(this)
        } else MessageDbFactory(mContext).of(number).apply {
            manager().nukeTable(mContext, number)
        }
    }
}

fun String?.toConversation(): Conversation {
    return Gson().fromJson(this, Conversation::class.java)
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(conversation: Conversation)

    @Query("UPDATE conversations SET read = 1 WHERE number = :number")
    fun markRead(number: String)

    @Query("UPDATE conversations SET time = :time WHERE number = :number")
    fun updateTime(number: String, time: Long)

    @Delete
    fun delete(conversation: Conversation)

    @Query("SELECT * FROM conversations WHERE number LIKE :number")
    fun findByNumber(number: String): Conversation?

    @Query("SELECT * FROM conversations WHERE number LIKE :number")
    fun getLive(number: String): LiveData<Conversation?>

    @Query("SELECT * FROM conversations LIMIT 1")
    fun loadSingle(): Conversation?

    @Query("SELECT * FROM conversations ORDER BY time DESC")
    fun loadAllPaged(): PagingSource<Int, Conversation>

    @Query("SELECT * FROM conversations")
    fun loadAllSync(): List<Conversation>

    @Query("SELECT COUNT(number) FROM conversations WHERE read = 0")
    fun getLiveUnreadCount(): LiveData<Int>
}

object ConversationComparator : DiffUtil.ItemCallback<Conversation>() {
    override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation) =
        oldItem.number == newItem.number

    override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation) =
        oldItem == newItem
}

class Converters {
    @TypeConverter
    fun listToJson(value: Array<Float>?): String = Gson().toJson(value)

    @TypeConverter
    fun jsonToList(value: String?): Array<Float> = Gson().fromJson(value, Array<Float>::class.java)
}

@Database(entities = [Conversation::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun manager(): ConversationDao
}

class ConversationDbFactory(private val mContext: Context) {
    fun of(label: Int, mainThread: Boolean = true) = Room.databaseBuilder(
        mContext, ConversationDatabase::class.java, label.toString()
    ).apply {
        if (mainThread) allowMainThreadQueries()
    }.build()
}