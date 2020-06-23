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
    val type: Int,
    val time: Long,
    var label: Int
): Serializable


@Dao
interface MessageDao {
    @Insert
    fun insert(message: Message)

    @Update
    fun update(message: Message)

    @Delete
    fun delete(message: Message)

    @Query("SELECT * FROM messages WHERE sender LIKE :sender")
    fun findBySender(sender: String): List<Message>

    @Query("SELECT * FROM messages ORDER BY time ASC")
    fun loadAll(): LiveData<List<Message>>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Message>

}

@Database(
    entities = [Message::class], version = 1)
abstract class MessageDatabase: RoomDatabase() {
    abstract fun manager(): MessageDao
}