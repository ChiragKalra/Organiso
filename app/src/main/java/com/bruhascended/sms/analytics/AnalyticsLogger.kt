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

package com.bruhascended.sms.analytics

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.bruhascended.sms.BuildConfig
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.MessageDatabase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class AnalyticsLogger(
    private val context: Context
) {
    private val mPref = PreferenceManager.getDefaultSharedPreferences(context)
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)


    fun reportSpam (conversation: Conversation) {
        if (!mPref.getBoolean("report_spam", false)) return

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("spam_report/${conversation.sender}")
        myRef.setValue(
            Room.databaseBuilder(
                context, MessageDatabase::class.java, conversation.sender
            ).build().apply {
                manager().loadAllSync()
                close()
            }
        )
    }

    fun reportBug (title: String, content: String, fileUri: Uri? = null) {
        val rn = System.currentTimeMillis().toString()
        log("bug_reported")

        val database = FirebaseDatabase.getInstance()
        val myRef = database.getReference("bug_report/${rn}")
        myRef.child("title").setValue(title)
        myRef.child("detail").setValue(content)

        if (fileUri != null) {
            val storageRef = FirebaseStorage.getInstance().reference
            val riversRef = storageRef.child("bug_report/${rn}")
            riversRef.putFile(fileUri)
        }
    }

    fun log (event: String, param: String = "default") {
        if (BuildConfig.DEBUG) return
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, param)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }
}