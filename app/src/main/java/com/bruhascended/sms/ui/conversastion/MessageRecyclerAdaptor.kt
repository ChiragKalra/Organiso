package com.bruhascended.sms.ui.conversastion

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageComparator
import com.bruhascended.sms.ml.displayFullTime
import com.bruhascended.sms.ui.ListSelectionManager
import com.bruhascended.sms.ui.ListSelectionManager.Companion.SelectionRecyclerAdaptor

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


class MessageRecyclerAdaptor(
    private val mContext: Context
): SelectionRecyclerAdaptor<Message, MessageViewHolder>(MessageComparator){
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private val highlightColor = mContext.getColor(R.color.textHighLight)

    lateinit var selectionManager: ListSelectionManager<Message>
    var searchKey: String? = null

    override fun getItemViewType(position: Int) = if (getItem(position)?.type == 1) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder(
            mContext,
            LayoutInflater.from(parent.context).inflate(
                if (viewType == 1) R.layout.item_message else R.layout.item_message_out,
                parent, false
            )
        )
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.apply {
            message = getItem(position) ?: return
            hideMedia()

            messageTextView.text = if (!searchKey.isNullOrBlank()) SpannableString(message.text).apply {
                var index = message.text.indexOf(searchKey!!, ignoreCase = true)
                while (index >= 0) {
                    setSpan(BackgroundColorSpan(highlightColor), index, index+searchKey!!.length, flag)
                    index = message.text.indexOf(searchKey!!, index+1, ignoreCase = true)
                }
            } else message.text
            timeTextView.text = displayFullTime(message.time, mContext)

            if (message.type != 1) {
                statusTextView!!.visibility = View.VISIBLE
                statusTextView.setTextColor(textColor)
                statusTextView.text =  when {
                    message.delivered -> "delivered"
                    message.type == 2 -> "sent"
                    message.type == 6 -> "queued"
                    else -> {
                        statusTextView.setTextColor(mContext.getColor(R.color.red))
                        "failed"
                    }
                }
            }

            if (message.path != null) {
                showMedia()
                if (message.text == "") messageTextView.visibility = View.GONE
            }

            root.apply {
                if (::selectionManager.isInitialized && selectionManager.isSelected(position))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener{
                    onItemClickListener(holder)
                }
                setOnLongClickListener {
                    onItemLongClickListener(holder)
                }
            }
        }
    }

    var onItemClickListener: (MessageViewHolder) -> Unit = {
        val pos = it.absoluteAdapterPosition
        if (selectionManager.isActive) {
            selectionManager.toggleItem(pos)
            if (selectionManager.isSelected(pos)) {
                Handler(mContext.mainLooper).postDelayed({
                    (mContext as AppCompatActivity).runOnUiThread{
                        if (selectionManager.isSelected(pos))
                            it.root.setBackgroundColor(it.selectedColor)
                    }
                }, 200)
            } else {
                it.root.background = it.defaultBackground
            }
        } // TODO else show time
    }

    var onItemLongClickListener: (MessageViewHolder) -> Boolean = {
        val pos = it.absoluteAdapterPosition
        selectionManager.toggleItem(pos)
        if (selectionManager.isSelected(pos)) {
            it.root.setBackgroundColor(it.selectedColor)
        } else {
            it.root.background = it.defaultBackground
        }
        true
    }
}
