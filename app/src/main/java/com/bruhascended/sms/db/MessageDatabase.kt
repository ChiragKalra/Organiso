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
): Serializable


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

    @Query("SELECT * FROM messages WHERE text LIKE :key")
    fun search(key: String): List<Message>

    @Query("SELECT * FROM messages WHERE time LIKE :time")
    fun search(time: Long): List<Message>

    @Query("SELECT * FROM messages ORDER BY time ASC")
    fun loadAll(): LiveData<List<Message>>

    @Query("SELECT * FROM messages")
    fun loadAllSync(): List<Message>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Message>

}

@Database(entities = [Message::class], version = 1, exportSchema = false)
abstract class MessageDatabase: RoomDatabase() {
    abstract fun manager(): MessageDao
}