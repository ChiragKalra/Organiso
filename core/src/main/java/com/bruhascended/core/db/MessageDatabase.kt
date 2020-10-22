package com.bruhascended.core.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import com.bruhascended.core.constants.deleteSMS
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

@Entity(tableName = "messages")
data class Message (
    val text: String,
    var type: Int,
    val time: Long,
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,
    var delivered: Boolean = false,
    var path: String? = null
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message
        if (text != other.text) return false
        if (delivered != other.delivered) return false
        if (time != other.time) return false
        if (path != other.path) return false
        if (type != other.type) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Dao
interface MessageDao {
    @Insert
    fun insert(message: Message)

    @Transaction
    @Insert
    fun insertAll(messages: List<Message>)

    @Update
    fun update(message: Message)

    @Delete
    fun deleteFromInternal(message: Message)

    fun delete(mContext: Context, message: Message, sender: String) {
        Thread {
            deleteFromInternal(message)
            deleteSMS(mContext, message.text, sender)
        }.start()
    }

    @Query("DELETE FROM messages")
    fun nukeInternalTable()

    fun nukeTable(mContext: Context, sender: String) {
        Thread {
            nukeInternalTable()
            deleteSMS(mContext, null, sender)
        }.start()
    }

    @Query("SELECT * FROM messages WHERE LOWER(text) LIKE :key OR LOWER(text) LIKE :altKey ORDER BY time DESC")
    fun search(key: String, altKey: String=""): List<Message>

    @Query("SELECT * FROM messages WHERE LOWER(text) LIKE :key OR LOWER(text) LIKE :altKey ORDER BY time DESC")
    fun searchPaged(key: String, altKey: String=""): PagingSource<Int, Message>

    @Query("SELECT * FROM messages WHERE time LIKE :time")
    fun search(time: Long): List<Message>

    @Query("SELECT * FROM messages ORDER BY time DESC LIMIT 1")
    fun loadLast(): LiveData<Message?>

    @Query("SELECT * FROM messages ORDER BY time DESC LIMIT 1")
    fun loadLastSync(): Message?

    @Query("SELECT * FROM messages ORDER BY time DESC")
    fun loadAll(): LiveData<List<Message>>

    @Query("SELECT * FROM messages ORDER BY time DESC")
    fun loadAllPaged(): PagingSource<Int, Message>

    @Query("SELECT * FROM messages")
    fun loadAllSync(): List<Message>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Message>
}

object MessageComparator : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Message, newItem: Message) =
        oldItem == newItem
}

@Database(entities = [Message::class], version = 1, exportSchema = false)
abstract class MessageDatabase: RoomDatabase() {
    abstract fun manager(): MessageDao
}

class MessageDbFactory(
    private val mContext: Context
) {
    fun of(sender: String, mainThread: Boolean = true) = Room.databaseBuilder(
        mContext, MessageDatabase::class.java, sender
    ).apply {
        if (mainThread) allowMainThreadQueries()
    }.build()
}