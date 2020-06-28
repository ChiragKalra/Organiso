package com.bruhascended.sms.ui.listViewAdapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.*

class ConversationListViewAdaptor (context: Context, data: List<Conversation>) : BaseAdapter() {

    private val mContext: Context = context
    private val conversations: List<Conversation> = data

    private fun displayTime(time: Long): String {
        val smsTime = Calendar.getInstance()
        smsTime.timeInMillis = time

        val now = Calendar.getInstance()

        val timeFormatString = if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa"
        val dateFormatString = "d MMMM"
        val dateYearFormatString = "dd/MM/yyyy"
        return when {
            DateUtils.isToday(time) ->
                DateFormat.format(timeFormatString, smsTime).toString()
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) ->
                "Yesterday"
            now[Calendar.YEAR] == smsTime[Calendar.YEAR] ->
                DateFormat.format(dateFormatString, smsTime).toString()
            else ->
                DateFormat.format(dateYearFormatString, smsTime).toString()
        }
    }


    override fun getCount(): Int {
        return conversations.count()
    }

    override fun getItem(position: Int): Any {
        return conversations[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    @SuppressLint("ViewHolder", "SetTextI18n")
    override fun getView(
        position: Int, convertView: View?, parent: ViewGroup
    ): View {
        val cur = conversations[position]

        val layoutInflater = LayoutInflater.from(mContext)
        val root = layoutInflater.inflate(R.layout.item_conversation, parent, false)

        // val imageView: ImageView = root.findViewById(R.id.dp)
        val senderTextView: TextView = root.findViewById(R.id.sender)
        val messageTextView: TextView = root.findViewById(R.id.lastMessage)
        val timeTextView: TextView = root.findViewById(R.id.time)

        senderTextView.text = cur.name ?: cur.sender
        messageTextView.text = cur.lastSMS

        timeTextView.text = displayTime(cur.time)

        return root
    }
}