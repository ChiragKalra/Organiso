package com.bruhascended.sms.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber
import java.io.Serializable
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set
import kotlin.collections.sortBy
import kotlin.collections.toTypedArray


data class Contact (
    val name: String,
    val number: String
): Serializable


class ContactsManager(context: Context) {

    private val mContext = context
    private val map = HashMap<String, String>()

    fun retrieveContactPhoto(number: String): Bitmap? {
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

    fun getRaw(number: String): String {
        return if (number.startsWith("+")) {
            try {
                val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(mContext)
                val numberProto: Phonenumber.PhoneNumber = phoneUtil.parse(number, "")
                numberProto.nationalNumber.toString().replace(Regex("\\s"), "")
            } catch (e: NumberParseException) {
                number.replace(Regex("\\s"), "")
            }
        } else {
            number.replace(Regex("\\s"), "")
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
                    map[getRaw(phoneNo)] = name
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
                    list.add(Contact(name, getRaw(phoneNo)))
                }
                pCur?.close()
            }
        }
        cur?.close()
        val arr = list.toTypedArray()
        arr.sortBy {it.name}
        return arr
    }
}