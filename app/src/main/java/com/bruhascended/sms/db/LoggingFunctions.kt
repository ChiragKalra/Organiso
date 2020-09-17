package com.bruhascended.sms.db

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.firebase.database.FirebaseDatabase

fun reportSpam(activity: AppCompatActivity, conversation: Conversation) {
    if (!PreferenceManager.getDefaultSharedPreferences(activity)
            .getBoolean("report_spam", false)) return

    Room.databaseBuilder(
        activity, MessageDatabase::class.java, conversation.sender
    ).build().manager().also {
        it.loadAll().observe(activity,object: Observer<List<Message>>{
            override fun onChanged(t: List<Message>?) {
                it.loadAll().removeObserver(this)
                val database = FirebaseDatabase.getInstance()
                val myRef = database.getReference("spam_report/${conversation.sender}")
                myRef.setValue(t)
            }
        })
    }
}