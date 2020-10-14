package com.bruhascended.organiso.ui.conversation

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.organiso.R
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.db.MessageComparator
import com.bruhascended.organiso.services.MMSSender
import com.bruhascended.organiso.services.SMSSender
import com.bruhascended.organiso.ui.common.ListSelectionManager
import com.bruhascended.organiso.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.organiso.ui.common.MediaPreviewManager.Companion.getMimeType
import java.io.File

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
    private val mContext: Context,
    private val smsSender: SMSSender? = null,
    private val mmsSender: MMSSender? = null
): SelectionRecyclerAdaptor<Message, MessageViewHolder>(MessageComparator){

    lateinit var selectionManager: ListSelectionManager<Message>
    val isSelectionManagerNull get() = !::selectionManager.isInitialized
    var searchKey = ""

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
        holder.searchKey = searchKey
        holder.apply {
            message = getItem(position) ?: return
            onBind(smsSender != null)
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
                if (it.message.path == null) smsSender?.sendSMS(it.message.text, it.message.id)
                else {
                    val uri =  Uri.fromFile(File(it.message.path!!))
                    mmsSender?.sendMMS(it.message.text, uri, getMimeType(it.message.path!!), it.message.id)
                }
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
