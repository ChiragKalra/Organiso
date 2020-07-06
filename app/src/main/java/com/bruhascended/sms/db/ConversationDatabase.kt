package com.bruhascended.sms.db

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
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
    var label: Int
): Serializable


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

@Database(entities = [Conversation::class], version = 1)
abstract class ConversationDatabase : RoomDatabase() {
    abstract fun manager(): ConversationDao
}