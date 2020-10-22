package com.bruhascended.organiso.ui.conversation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.core.constants.MESSAGE_TYPE_INBOX
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.MessageComparator
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.ListSelectionManager
import com.bruhascended.organiso.common.MyPagingDataAdapter

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

class MessageRecyclerAdaptor (
    private val mContext: Context
): MyPagingDataAdapter<Message, MessageViewHolder>(MessageComparator) {

    private var retryCallBack: ((Message) -> Unit)? = null

    lateinit var selectionManager: ListSelectionManager<Message>
    val isSelectionManagerNull get() = !::selectionManager.isInitialized
    var searchKey = ""

    fun setOnRetry(callback: (Message) -> Unit) {
        retryCallBack = callback
    }

    override fun getItemViewType(position: Int) = if (getItem(position)?.type == MESSAGE_TYPE_INBOX) 1 else 0

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
        holder.searchKey = searchKey
        holder.apply {
            message = getItem(position) ?: return
            onBind(retryCallBack != null)
            root.apply {
                stopBgAnim()
                if (::selectionManager.isInitialized &&
                    selectionManager.isRangeSelection(position)) {
                    rangeSelectionAnim()
                } else if (::selectionManager.isInitialized && selectionManager.isSelected(position))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener{
                    onItemClickListener(holder)
                }
                setOnLongClickListener {
                    onItemLongClickListener(holder)
                }
            }
            messageTextView.apply {
                setOnClickListener {
                    if (selectionStart == -1 && selectionEnd == -1) {
                        //This condition will satisfy only when it is not an auto-linked text
                        //Fired only when you touch the part of the text that is not hyperlinked
                        onItemClickListener.invoke(holder)
                    }
                }
                setOnLongClickListener {
                    if (selectionStart == -1 && selectionEnd == -1) {
                        //This condition will satisfy only when it is not an auto-linked text
                        //Fired only when you touch the part of the text that is not hyperlinked
                        onItemLongClickListener(holder)
                    } else false
                }
            }
        }
    }

    var onItemClickListener: (MessageViewHolder) -> Unit = {
        val pos = it.absoluteAdapterPosition
        when {
            selectionManager.isActive -> {
                selectionManager.toggleItem(pos)
                when {
                    selectionManager.isRangeSelection(pos) ->
                        it.rangeSelectionAnim()
                    selectionManager.isSelected(pos) ->
                        Handler(mContext.mainLooper).postDelayed({
                            (mContext as AppCompatActivity).runOnUiThread{
                                if (selectionManager.isSelected(pos))
                                    it.root.setBackgroundColor(it.selectedColor)
                            }
                        }, 200)
                    else ->
                        it.root.background = it.defaultBackground
                }
            }
            it.failed -> {
                retryCallBack?.invoke(it.message)
            }
            else -> {
                it.showTime()
            }
        }
    }

    var onItemLongClickListener: (MessageViewHolder) -> Boolean = {
        val pos = it.absoluteAdapterPosition
        selectionManager.toggleItem(pos)
        when {
            selectionManager.isRangeSelection(pos) ->
                it.rangeSelectionAnim()
            selectionManager.isSelected(pos) ->
                it.root.setBackgroundColor(it.selectedColor)
            else ->
                it.root.background = it.defaultBackground
        }
        true
    }
}
