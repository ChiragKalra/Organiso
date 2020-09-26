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

package com.bruhascended.sms.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import java.io.Serializable


@Entity(tableName = "messages")
data class Message (
    @PrimaryKey(autoGenerate = true)
    val id: Long?,
    val sender: String,
    val text: String,
    var type: Int,
    val time: Long,
    var label: Int,
    var delivered: Boolean = false,
    var path: String? = null
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message
        if (sender != other.sender) return false
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
    fun delete(message: Message)

    @Query("DELETE FROM messages")
    fun nukeTable()

    @Query("SELECT * FROM messages WHERE text LIKE :key OR text LIKE :altKey ORDER BY time DESC")
    fun search(key: String, altKey: String=""): List<Message>

    @Query("SELECT * FROM messages WHERE text LIKE :key OR text LIKE :altKey ORDER BY time DESC")
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