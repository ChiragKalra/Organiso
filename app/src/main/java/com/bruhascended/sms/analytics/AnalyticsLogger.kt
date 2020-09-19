package com.bruhascended.sms.analytics

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import androidx.room.Room
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
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, param)
        }
        firebaseAnalytics.logEvent(event, bundle)
    }
}