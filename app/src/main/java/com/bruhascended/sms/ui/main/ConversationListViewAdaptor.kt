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
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
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
import android.widget.ImageView
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.dpMemoryCache
import com.bruhascended.sms.ui.ListSelectionManager
import java.util.*


@SuppressLint("ResourceType")
class ConversationListViewAdaptor(
    private val mContext: Context,
    private val conversations: List<Conversation>
) : RecyclerView.Adapter<ConversationListViewAdaptor.ConversationViewHolder>() {

    private var cm: ContactsManager = ContactsManager(mContext)
    private var mSelectedItemsIds = SparseBooleanArray()
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

    lateinit var selectionManager: ListSelectionManager<Conversation>

    private var colors: Array<Int> = arrayOf(
        ContextCompat.getColor(mContext, R.color.red),
        ContextCompat.getColor(mContext, R.color.blue),
        ContextCompat.getColor(mContext, R.color.purple),
        ContextCompat.getColor(mContext, R.color.green),
        ContextCompat.getColor(mContext, R.color.teal),
        ContextCompat.getColor(mContext, R.color.orange)
    )

    inner class ConversationViewHolder(val root: View): RecyclerView.ViewHolder(root) {
        val imageView: QuickContactBadge = root.findViewById(R.id.dp)
        val muteImage: ImageView = root.findViewById(R.id.mutedImage)
        val senderTextView: TextView = root.findViewById(R.id.sender)
        val messageTextView: TextView = root.findViewById(R.id.lastMessage)
        val timeTextView: TextView = root.findViewById(R.id.time)
    }

    private fun displayTime(time: Long): String {
        val smsTime = Calendar.getInstance().apply { timeInMillis = time }
        val now = Calendar.getInstance()

        return when {
            DateUtils.isToday(time) -> DateFormat.format(
                if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa",
                smsTime
            ).toString()

            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"

            now[Calendar.WEEK_OF_YEAR] == smsTime[Calendar.WEEK_OF_YEAR] -> DateFormat.format(
                "EEEE", smsTime
            ).toString()

            now[Calendar.YEAR] == smsTime[Calendar.YEAR] -> DateFormat.format(
                "d MMMM", smsTime
            ).toString()

            else -> DateFormat.format("dd/MM/yyyy", smsTime).toString()
        }
    }

    override fun getItemCount(): Int = conversations.count()

    override fun getItemId(pos: Int) = conversations[pos].id!!

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.apply {
            val cur = conversations[position]

            root.apply {
                val tp = mContext.obtainStyledAttributes(intArrayOf(
                    R.attr.multiChoiceSelectorColor,
                    android.R.attr.selectableItemBackground
                ))
                val defaultBackground = tp.getDrawable(1)!!
                val selectedColor = tp.getColor(0, 0)
                tp.recycle()
                if (selectionManager.isSelected(position))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener{
                    if (selectionManager.isActive) {
                        selectionManager.toggleItem(position)
                        if (selectionManager.isSelected(position)) {
                            Handler(context.mainLooper).postDelayed({
                                (mContext as AppCompatActivity).runOnUiThread{
                                    if (selectionManager.isSelected(position))
                                        setBackgroundColor(selectedColor)
                                }
                            }, 200)
                        } else {
                            background = defaultBackground
                        }
                    }
                    else mContext.startActivity(
                        Intent(mContext, ConversationActivity::class.java)
                            .putExtra("ye", conversations[position])
                    )
                }
                setOnLongClickListener {
                    selectionManager.toggleItem(position)
                    if (selectionManager.isSelected(position)) {
                        if (selectionManager.isSelected(position))
                            setBackgroundColor(selectedColor)
                    } else {
                        background = defaultBackground
                    }
                    true
                }
            }

            senderTextView.text = cur.name ?: cur.sender
            timeTextView.text = displayTime(cur.time)
            messageTextView.text = if (cur.lastMMS) {
                val str = SpannableString("Media ${cur.lastSMS}")
                val color = mContext.getColor(R.color.colorAccent)
                str.setSpan(ForegroundColorSpan(color), 0, 6, flag)
                if (!cur.read) str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
                str
            } else {
                val str = SpannableString(cur.lastSMS)
                if (!cur.read) str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
                str
            }

            muteImage.visibility = if (cur.isMuted) View.VISIBLE else View.GONE
            imageView.apply {
                assignContactFromPhone(cur.sender, true)
                setMode(ContactsContract.QuickContact.MODE_LARGE)
                setBackgroundColor(colors[position % colors.size])
                setPadding(0, 0, 0, 0)

                if (cur.sender.first().isLetter()) {
                    setImageResource(R.drawable.ic_bot)
                    isEnabled = false
                    val density = mContext.resources.displayMetrics.density
                    val dps = 12 * density.toInt()
                    setPadding(dps, dps, dps, dps)
                } else if (cur.name != null) {
                    if (dpMemoryCache.containsKey(cur.sender)) {
                        val dp = dpMemoryCache[cur.sender]
                        if (dp != null) setImageBitmap(dp)
                        else setImageResource(R.drawable.ic_baseline_person_48)
                    } else {
                        setImageResource(R.drawable.ic_baseline_person_48)
                        Thread {
                            dpMemoryCache[cur.sender] = cm.retrieveContactPhoto(cur.sender)
                            val dp = dpMemoryCache[cur.sender]
                            (mContext as Activity).runOnUiThread {
                                if (dp != null) setImageBitmap(dp)
                            }
                        }.start()
                    }
                } else {
                    setImageResource(R.drawable.ic_baseline_person_48)
                    isEnabled = false
                }
            }


        }
    }
}