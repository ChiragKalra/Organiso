package com.bruhascended.organiso.ui.search

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import com.bruhascended.organiso.*
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.Message
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.conversation.MessageViewHolder
import com.bruhascended.organiso.ui.main.ConversationViewHolder

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

class SearchResultViewHolder(
    val mContext: Context,
    val type: Int,
    private val mAdaptor: SearchRecyclerAdaptor,
    val root: View
): ScrollEffectFactory.ScrollEffectViewHolder(root) {

    data class ResultItem(
        val type: Int,
        val categoryHeader: Int = -1,
        val conversation: Conversation? = null,
        val message: Message? = null
    )

    object ResultItemComparator : DiffUtil.ItemCallback<ResultItem>() {
        override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem) =
            oldItem == newItem
        override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem) =
            oldItem == newItem
    }

    lateinit var item: ResultItem
    private lateinit var conversationViewHolder: ConversationViewHolder
    private lateinit var messageViewHolder: MessageViewHolder

    private val colors = mContext.resources.getIntArray(R.array.colors)
    init {
        when (type) {
            TYPE_CONVERSATION, TYPE_CONTACT ->
                conversationViewHolder = ConversationViewHolder(root, mContext)
            TYPE_MESSAGE_SENT, TYPE_MESSAGE_RECEIVED ->
                messageViewHolder = MessageViewHolder(mContext, root)
        }
    }

    fun onBind(resultItem: ResultItem) {
        item = resultItem
        val pos = absoluteAdapterPosition
        when(type) {
            TYPE_CONVERSATION, TYPE_CONTACT -> {
                conversationViewHolder.apply {
                    conversation = item.conversation!!
                    onBind()
                    imageView.setBackgroundColor(
                        colors[pos % colors.size]
                    )
                    root.apply {
                        background = defaultBackground
                        setOnClickListener { mAdaptor.doOnConversationClick(conversation) }
                        setOnLongClickListener { false }
                    }

                    if (type == TYPE_CONTACT) {
                        senderTextView.apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                            val lp = layoutParams as ConstraintLayout.LayoutParams
                            lp.bottomToBottom = R.id.root
                            layoutParams = lp
                        }

                        timeTextView.visibility = View.GONE
                        messageTextView.visibility = View.GONE
                    }
                }
            }
            TYPE_MESSAGE_SENT, TYPE_MESSAGE_RECEIVED -> {
                messageViewHolder.apply {
                    searchKey = mAdaptor.searchKey
                    message = item.message!!
                    onBind()
                    root.apply {
                        background = defaultBackground
                        setOnClickListener { mAdaptor.doOnMessageClick(message.id!! to item.conversation!!) }
                        setOnLongClickListener { false }
                    }
                }
            }
            TYPE_HEADER -> {
                val labelTextView: TextView = root.findViewById(R.id.label)
                if (item.categoryHeader == HEADER_CONTACTS)
                    labelTextView.text = mContext.getString(R.string.from_contacts)
                else {
                    val label = item.categoryHeader - (if (item.categoryHeader > 9) 10 else 0)
                    val labelText = mContext.resources.getStringArray(R.array.labels)[label]
                    labelTextView.text = when {
                        item.categoryHeader > 9 ->
                            mContext.getString(R.string.messages_in_label, labelText)
                        else -> labelText
                    }
                }
            }
            TYPE_FOOTER -> {
                val loading = root.findViewById<ProgressBar>(R.id.loading)
                val empty = root.findViewById<TextView>(R.id.noResults)
                val end = root.findViewById<TextView>(R.id.endOfResults)

                if (mAdaptor.isLoaded) {
                    loading.isVisible = false
                    end.isVisible = false
                    empty.isVisible = false
                    if (mAdaptor.itemCount != 1) end.isVisible = true
                    else empty.isVisible = true
                } else {
                    loading.isVisible = true
                    end.isVisible = false
                    empty.isVisible = false
                }

                mAdaptor.doOnLoaded = {
                    loading.isVisible = false
                    end.isVisible = false
                    empty.isVisible = false
                    mAdaptor.isLoaded = true

                    if (mAdaptor.itemCount != 1) end.isVisible = true
                    else empty.isVisible = true
                }
            }
        }

    }
}