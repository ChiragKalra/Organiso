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

package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.provider.ContactsContract
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.R
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.ui.dpMemoryCache
import com.bruhascended.sms.ui.mainViewModel
import java.util.*


class ConversationListViewAdaptor(
    private val mContext: Context,
    private val conversations: List<Conversation>
) : BaseAdapter() {

    private var mSelectedItemsIds = SparseBooleanArray()
    private var colors: Array<Int> = arrayOf(
        ContextCompat.getColor(mContext, R.color.red),
        ContextCompat.getColor(mContext, R.color.blue),
        ContextCompat.getColor(mContext, R.color.purple),
        ContextCompat.getColor(mContext, R.color.green),
        ContextCompat.getColor(mContext, R.color.teal),
        ContextCompat.getColor(mContext, R.color.orange)
    )
    private var cm: ContactsManager = ContactsManager(mContext)

    private fun displayTime(time: Long): String {
        val smsTime = Calendar.getInstance()
        smsTime.timeInMillis = time
        val now = Calendar.getInstance()
        val timeFormatString = if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa"
        val dateFormatString = "d MMMM"
        val dateYearFormatString = "dd/MM/yyyy"
        return when {
            DateUtils.isToday(time) -> DateFormat.format(timeFormatString, smsTime).toString()
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
            now[Calendar.YEAR] == smsTime[Calendar.YEAR] -> DateFormat.format(
                dateFormatString,
                smsTime
            ).toString()
            else -> DateFormat.format(dateYearFormatString, smsTime).toString()
        }
    }

    override fun getCount(): Int = conversations.count()
    override fun getItem(position: Int) = conversations[position]
    override fun getItemId(position: Int)= position.toLong()

    fun getSelectedIds() = mSelectedItemsIds
    fun toggleSelection(position: Int) = selectView(position, !mSelectedItemsIds.get(position))

    fun removeSelection() {
        mSelectedItemsIds = SparseBooleanArray()
    }

    private fun selectView(position: Int, value: Boolean) {
        if (value) mSelectedItemsIds.put(position, value)
        else mSelectedItemsIds.delete(position)
    }

    @SuppressLint("ViewHolder", "SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cur = conversations[position]

        val layoutInflater = LayoutInflater.from(mContext)
        val root = layoutInflater.inflate(R.layout.item_conversation, parent, false)

        val imageView: QuickContactBadge = root.findViewById(R.id.dp)
        val muteImage: ImageView = root.findViewById(R.id.mutedImage)
        val senderTextView: TextView = root.findViewById(R.id.sender)
        val messageTextView: TextView = root.findViewById(R.id.lastMessage)
        val timeTextView: TextView = root.findViewById(R.id.time)

        if (cur.name == null) {
            mainViewModel.contacts.observe(
                mContext as AppCompatActivity,
                object : Observer<Array<Contact>?> {
                    override fun onChanged(contacts: Array<Contact>?) {
                        mainViewModel.contacts.removeObserver(this)
                        if (contacts == null) return
                        for (contact in contacts) {
                            if (contact.number == cur.sender) {
                                cur.name = contact.name
                                mainViewModel.daos[cur.label].update(cur)
                                break
                            }
                        }

                    }
                }
            )
        }
        senderTextView.text = cur.name ?: cur.sender

        val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

        messageTextView.text = if (cur.lastMMS) {
            val str = SpannableString("Media: ${cur.lastSMS}")
            val color = mContext.getColor(R.color.colorAccent)
            str.setSpan(ForegroundColorSpan(color), 0, 6, flag)
            if (!cur.read) str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
            str
        } else {
            val str = SpannableString(cur.lastSMS)
            if (!cur.read) str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
            str
        }

        if (cur.isMuted) muteImage.visibility = View.VISIBLE

        timeTextView.text = displayTime(cur.time)

        imageView.assignContactFromPhone(cur.sender, true)
        imageView.setMode(ContactsContract.QuickContact.MODE_LARGE)

        imageView.setBackgroundColor(colors[position % colors.size])
        imageView.performClick()

        if (cur.sender.first().isLetter()) {
            imageView.setImageResource(R.drawable.ic_bot)
            imageView.isEnabled = false
            val density = mContext.resources.displayMetrics.density
            val dps = 12 * density.toInt()
            imageView.setPadding(dps, dps, dps, dps)
        } else if (cur.name != null) {
            if (dpMemoryCache.containsKey(cur.sender)) {
                val dp = dpMemoryCache[cur.sender]
                if (dp != null) imageView.setImageBitmap(dp)
            } else Thread {
                dpMemoryCache[cur.sender] = cm.retrieveContactPhoto(cur.sender)
                val dp = dpMemoryCache[cur.sender]
                (mContext as Activity).runOnUiThread {
                    if (dp != null) imageView.setImageBitmap(dp)
                }
            }.start()
        }
        return root
    }
}