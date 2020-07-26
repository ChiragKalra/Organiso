package com.bruhascended.sms.ui.listViewAdapter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.QuickContactBadge
import androidx.core.content.ContextCompat
import com.bruhascended.sms.data.retrieveContactPhoto
import java.util.*
import kotlin.collections.HashMap

class ConversationListViewAdaptor (context: Context, data: List<Conversation>) : BaseAdapter() {

    private val mContext: Context = context
    private val conversations: List<Conversation> = data
    private var memoryCache = HashMap<String, Bitmap?>()
    private var colors: Array<Int>

    init {
        colors = arrayOf(
            ContextCompat.getColor(mContext, R.color.red),
            ContextCompat.getColor(mContext, R.color.blue),
            ContextCompat.getColor(mContext, R.color.purple),
            ContextCompat.getColor(mContext, R.color.green),
            ContextCompat.getColor(mContext, R.color.teal),
            ContextCompat.getColor(mContext, R.color.orange)
        )
    }

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

    override fun getCount(): Int = conversations.count()
    override fun getItem(position: Int) = conversations[position]
    override fun getItemId(position: Int)= position.toLong()

    @SuppressLint("ViewHolder", "SetTextI18n")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cur = conversations[position]

        val layoutInflater = LayoutInflater.from(mContext)
        val root = layoutInflater.inflate(R.layout.item_conversation, parent, false)

        val imageView: QuickContactBadge = root.findViewById(R.id.dp)
        val senderTextView: TextView = root.findViewById(R.id.sender)
        val messageTextView: TextView = root.findViewById(R.id.lastMessage)
        val timeTextView: TextView = root.findViewById(R.id.time)

        senderTextView.text = cur.name ?: cur.sender
        messageTextView.text = cur.lastSMS

        timeTextView.text = displayTime(cur.time)

        imageView.assignContactFromPhone(cur.sender, true)
        imageView.setMode(ContactsContract.QuickContact.MODE_LARGE)

        imageView.setBackgroundColor(colors[position % colors.size])

        if (cur.sender.first().isLetter()) {
            imageView.setImageResource(R.drawable.ic_business)
            imageView.isEnabled = false
            val density = mContext.resources.displayMetrics.density
            val dps = 12 * density.toInt()
            imageView.setPadding(dps,dps,dps,dps)
        } else if (cur.name != null) {
            if (memoryCache.containsKey(cur.sender)) {
                val dp = memoryCache[cur.sender]
                if (dp != null) imageView.setImageBitmap(dp)
            } else Thread( Runnable {
                memoryCache[cur.sender] = retrieveContactPhoto(mContext, cur.sender)
                val dp = memoryCache[cur.sender]
                (mContext as Activity).runOnUiThread {
                    if (dp != null) imageView.setImageBitmap(dp)
                }
            }).start()
        }
        return root
    }
}