package com.bruhascended.sms.ui.listViewAdapter

import android.annotation.SuppressLint
import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Message
import java.util.*

class MessageListViewAdaptor (context: Context, data: List<Message>) : BaseAdapter() {

    private val mContext: Context = context
    private val messages: List<Message> = data

    private fun displayTime(time: Long): String {
        val smsTime = Calendar.getInstance()
        smsTime.timeInMillis = time

        val now = Calendar.getInstance()

        val timeFormatString = if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa"
        val timeString = DateFormat.format(timeFormatString, smsTime).toString()
        val dateFormatString = "d MMMM"
        val dateYearFormatString = "dd/MM/yyyy"
        return when {
            DateUtils.isToday(time) -> timeString
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "$timeString,\nYesterday"
            now[Calendar.YEAR] == smsTime[Calendar.YEAR] ->
                timeString + ",\n" + DateFormat.format(dateFormatString, smsTime).toString()
            else ->
                timeString + ",\n" + DateFormat.format(dateYearFormatString, smsTime).toString()
        }
    }

    override fun getCount(): Int {
        return messages.count()
    }

    override fun getItem(position: Int): Any {
        return messages[position]
    }

    override fun getItemId(position: Int): Long {
        return messages[position].id!!
    }

    @SuppressLint("ViewHolder", "SetTextI18n")
    override fun getView(
        position: Int, convertView: View?, parent: ViewGroup
    ): View {
        val layoutInflater = LayoutInflater.from(mContext)
        val root = layoutInflater.inflate(R.layout.item_message, parent, false)

        val rootLayout : ConstraintLayout = root.findViewById(R.id.rootLayout)
        val messageTextView: TextView = root.findViewById(R.id.message)
        val timeTextView: TextView = root.findViewById(R.id.time)

        messageTextView.text = messages[position].text
        timeTextView.text = displayTime(messages[position].time)

        if (messages[position].type != 1) {
            messageTextView.background = mContext.getDrawable(R.drawable.bg_message_colored)
        }
        return root
    }
}
