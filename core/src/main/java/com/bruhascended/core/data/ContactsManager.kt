package com.bruhascended.core.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import com.bruhascended.core.db.Contact
import com.bruhascended.core.constants.*
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.collections.sortBy
import kotlin.collections.toTypedArray

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

class ContactsManager (
    private val mContext: Context
) {
    private val map = HashMap<String, String>()

    private fun retrieveContactPhoto(number: String): Bitmap? {
        val contentResolver = mContext.contentResolver
        var contactId: String? = null
        val uri: Uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup._ID
        )
        val cursor = contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )
        if (cursor != null) {
            while (cursor.moveToNext()) {
                contactId =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
            }
            cursor.close()
        }
        var photo: Bitmap? = null
        if (contactId != null) {
            val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                mContext.contentResolver,
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId.toLong())
            )
            if (inputStream != null) photo = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
        }
        return photo
    }

    fun getClean(number: String): String {
        return if (number.startsWith("+")) {
            try {
                val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(mContext)
                val numberProto: Phonenumber.PhoneNumber = phoneUtil.parse(number, "")
                numberProto.nationalNumber.toString().filter { it.isLetterOrDigit() }
            } catch (e: NumberParseException) {
                number.filter { it.isLetterOrDigit() }
            }
        } else {
            number.filter { it.isLetterOrDigit() }
        }
    }

    fun getContactsHashMap(): HashMap<String, String> {
        val cr: ContentResolver = mContext.contentResolver
        val cur: Cursor? = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        )
        while (cur != null && cur.moveToNext()) {
            val id: String = cur.getString(
                cur.getColumnIndex(ContactsContract.Contacts._ID)
            )
            val name: String? = cur.getString(
                cur.getColumnIndex(
                    ContactsContract.Contacts.DISPLAY_NAME
                )
            )
            if (name != null && cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                val pCur: Cursor? = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                while (pCur != null && pCur.moveToNext()) {
                    val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    map[getClean(phoneNo)] = name
                }
                pCur?.close()
            }
        }
        cur?.close()
        return map
    }

    fun getContactsList(): Array<Contact> {
        val list = HashSet<Contact>()

        val cr: ContentResolver = mContext.contentResolver
        val cur: Cursor? = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        )
        while (cur != null && cur.moveToNext()) {
            val id: String = cur.getString(
                cur.getColumnIndex(ContactsContract.Contacts._ID)
            )
            val name: String? = cur.getString(
                cur.getColumnIndex(
                    ContactsContract.Contacts.DISPLAY_NAME
                )
            )
            if (name != null && cur.getInt(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0) {
                val pCur: Cursor? = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(id),
                    null
                )
                while (pCur != null && pCur.moveToNext()) {
                    val phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    list.add(Contact(name, getClean(phoneNo), phoneNo, id.toInt()))
                }
                pCur?.close()
            }
        }
        cur?.close()
        val arr = list.toTypedArray()
        arr.sortBy {it.name.toLowerCase(Locale.ROOT) }
         Thread {
            arr.forEach {
                val bm = retrieveContactPhoto(it.clean)
                if (bm != null) {
                    val des = File(mContext.filesDir, it.clean)
                    bm.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(des))
                    mContext.sendBroadcast(Intent(ACTION_UPDATE_DP).putExtra(EXTRA_SENDER, it.clean))
                }
            }
        }.start()
        return arr
    }
}