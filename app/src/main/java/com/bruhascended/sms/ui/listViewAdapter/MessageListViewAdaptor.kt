package com.bruhascended.sms.ui.listViewAdapter

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Message
import java.util.*


class MessageListViewAdaptor (context: Context, data: List<Message>) : BaseAdapter() {

    private val mContext: Context = context
    private var messages: List<Message> = data
    private var mSelectedItemsIds = SparseBooleanArray()

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

    override fun getCount() = messages.size
    override fun getItem(position: Int) = messages[position]
    override fun getItemId(position: Int) = messages[position].id!!

    fun getSelectedIds() = mSelectedItemsIds
    fun toggleSelection(position: Int) = selectView(position, !mSelectedItemsIds.get(position))

    fun removeSelection() {
        mSelectedItemsIds = SparseBooleanArray()
    }

    private fun selectView(position: Int, value: Boolean) {
        if (value) mSelectedItemsIds.put(position, value)
        else mSelectedItemsIds.delete(position)
    }


    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val layoutInflater = LayoutInflater.from(mContext)
        val root =  if (messages[position].type != 1)
            layoutInflater.inflate(R.layout.item_message_out, parent, false)
        else layoutInflater.inflate(R.layout.item_message, parent, false)

        val messageTextView: TextView = root.findViewById(R.id.message)
        val timeTextView: TextView = root.findViewById(R.id.time)

        messageTextView.text = messages[position].text
        timeTextView.text = displayTime(messages[position].time)

        return root
    }
}
