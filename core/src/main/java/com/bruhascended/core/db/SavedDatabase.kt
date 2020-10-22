package com.bruhascended.core.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
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

@Entity(tableName = "saved")
data class Saved (
    val text: String,
    val time: Long,
    var type: Int,
    var tag: String,
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null,
    val sender: String? = null,
    var messageId: Long? = null,
    var path: String? = null,
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Saved
        if (text != other.text) return false
        if (time != other.time) return false
        if (path != other.path) return false
        if (tag != other.tag) return false
        if (type != other.type) return false
        if (sender != other.sender) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

@Dao
interface SavedDao {
    @Insert
    fun insert(saved: Saved)

    @Transaction
    @Insert
    fun insertAll(saved: List<Saved>)

    @Update
    fun update(saved: Saved)

    @Delete
    fun delete(saved: Saved)

    @Query("DELETE FROM saved")
    fun nukeTable()

    @Query("SELECT * FROM saved WHERE LOWER(text) LIKE :key OR LOWER(text) LIKE :altKey ORDER BY time DESC")
    fun search(key: String, altKey: String=""): List<Saved>

    @Query("SELECT * FROM saved WHERE LOWER(text) LIKE :key OR LOWER(text) LIKE :altKey ORDER BY time DESC")
    fun searchPaged(key: String, altKey: String=""): PagingSource<Int, Saved>

    @Query("SELECT * FROM saved WHERE time LIKE :time")
    fun search(time: Long): List<Saved>

    @Query("SELECT * FROM saved ORDER BY time DESC LIMIT 1")
    fun loadLast(): LiveData<Saved?>

    @Query("SELECT * FROM saved LIMIT 1")
    fun loadSingleSync(): Saved?

    @Query("SELECT * FROM saved ORDER BY time DESC")
    fun loadAll(): LiveData<List<Saved>>

    @Query("SELECT * FROM saved WHERE tag LIKE :tag ORDER BY time DESC")
    fun loadPagedFrom(tag: String): PagingSource<Int, Saved>

    @Query("SELECT * FROM saved")
    fun loadAllSync(): List<Saved>

    @Query("SELECT DISTINCT tag FROM saved")
    fun loadPagedTags(): PagingSource<Int, String>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): List<Saved>
}

object SavedComparator : DiffUtil.ItemCallback<Saved>() {
    override fun areItemsTheSame(oldItem: Saved, newItem: Saved) =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Saved, newItem: Saved) =
        oldItem == newItem
}

@Database(entities = [Saved::class], version = 1, exportSchema = false)
abstract class SavedDatabase: RoomDatabase() {
    abstract fun manager(): SavedDao
}

class SavedDbFactory(
    private val mContext: Context
) {
    fun get() = Room.databaseBuilder(
        mContext, SavedDatabase::class.java, "saved"
    ).allowMainThreadQueries().build()
}