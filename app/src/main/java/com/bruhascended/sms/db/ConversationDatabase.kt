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
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.*
import com.google.gson.Gson
import java.io.Serializable


@Entity(tableName = "conversations")
data class Conversation (
    @PrimaryKey(autoGenerate = true)
    var id: Long?,
    val sender: String,
    var name: String?,
    var dp: String,
    var read: Boolean,
    var time: Long,
    var lastSMS: String,
    var label: Int,
    var forceLabel: Int,
    var probs: FloatArray,
    var isMuted: Boolean = false,
    var lastMMS: Boolean = false
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Conversation
        if (sender != other.sender) return false
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

    @Query("SELECT * FROM conversations WHERE sender LIKE :sender OR name LIKE :sender")
    fun findBySender(sender: String): List<Conversation>

    @Query("SELECT * FROM conversations ORDER BY time DESC")
    fun loadAll(): LiveData<List<Conversation>>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Conversation>

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
