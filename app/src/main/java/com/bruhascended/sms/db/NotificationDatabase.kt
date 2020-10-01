package com.bruhascended.sms.db

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
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

@Entity(tableName = "notifications")
data class Notification (
    @PrimaryKey(autoGenerate = true)
    val sender: String,
    val text: String,
    val time: Long,
    val label: Int,
    var path: String?,
    val fromUser: Boolean,
    var id: Int? = null
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message
        if (sender != other.sender) return false
        if (text != other.text) return false
        if (time != other.time) return false
        if (path != other.path) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}


@Dao
interface NotificationDao {
    @Insert
    fun insert(notification: Notification)

    @Transaction
    @Insert
    fun insertAll(notifications: List<Notification>)

    @Update
    fun update(notification: Notification)

    @Delete
    fun delete(notification: Notification)

    @Query("SELECT * FROM notifications WHERE sender LIKE :sender")
    fun findBySender(sender: String): List<Notification>

    @Query("SELECT * FROM notifications WHERE label LIKE :label")
    fun loadFromLabel(label: Int): List<Notification>

    @Query("SELECT * FROM notifications")
    fun loadAllSync(): List<Notification>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Notification>

}

@Database(entities = [Notification::class], version = 1, exportSchema = false)
abstract class NotificationDatabase: RoomDatabase() {
    abstract fun manager(): NotificationDao
}