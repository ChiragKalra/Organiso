package com.bruhascended.core.db

import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.recyclerview.widget.DiffUtil
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
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

@Entity(tableName = "contacts")
data class Contact (
    var name: String,
    @PrimaryKey
    val number: String,
    val contactId: Int
): Serializable {
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false

        other as Contact
        return number == other.number
    }
    override fun hashCode() = abs(number.hashCode())
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(contact: Contact)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(contacts: Array<Contact>)

    @Delete
    fun delete(contact: Contact)

    @Query("DELETE FROM contacts")
    fun nukeTable()

    @Query("SELECT * FROM contacts WHERE number LIKE :sender")
    fun findByNumber(sender: String): Contact?

    @Query("SELECT name FROM contacts WHERE number LIKE :sender")
    fun getLive(sender: String): LiveData<String?>

    @Query("SELECT * FROM contacts")
    fun loadAllSync(): Array<Contact>

    @Query("SELECT * FROM contacts")
    fun loadAllPaged(): PagingSource<Int, Contact>

    @Query("SELECT * FROM contacts WHERE LOWER(name) LIKE :key OR LOWER(name) LIKE :altKey OR number LIKE :key or number like :altKey")
    fun searchPaged(key: String, altKey: String=""): PagingSource<Int, Contact>

    @RawQuery
    fun findByQuery(query: SupportSQLiteQuery): Array<Contact>

}

object ContactComparator : DiffUtil.ItemCallback<Contact>() {
    override fun areItemsTheSame(oldItem: Contact, newItem: Contact) =
        oldItem.number == newItem.number

    override fun areContentsTheSame(oldItem: Contact, newItem: Contact) =
        oldItem == newItem
}

@Database(entities = [Contact::class], version = 1, exportSchema = false)
abstract class ContactDatabase: RoomDatabase() {
    abstract fun manager(): ContactDao
}

