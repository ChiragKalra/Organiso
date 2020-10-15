package com.bruhascended.organiso.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import com.bruhascended.organiso.R
import com.bruhascended.organiso.data.SMSManager.Companion.labelText
import com.bruhascended.organiso.db.Conversation
import com.bruhascended.organiso.db.Message
import com.bruhascended.organiso.ui.common.ScrollEffectFactory
import com.bruhascended.organiso.ui.conversation.MessageViewHolder
import com.bruhascended.organiso.ui.main.ConversationRecyclerAdaptor
import com.bruhascended.organiso.ui.main.ConversationViewHolder


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
        override fun areItemsTheSame(oldItem: ResultItem, newItem: ResultItem) = oldItem == newItem
        override fun areContentsTheSame(oldItem: ResultItem, newItem: ResultItem) = oldItem == newItem
    }

    lateinit var item: ResultItem
    private lateinit var conversationViewHolder: ConversationViewHolder
    private lateinit var messageViewHolder: MessageViewHolder

    private val colors: Array<Int> = Array(ConversationRecyclerAdaptor.colorRes.size) {
        ContextCompat.getColor(mContext, ConversationRecyclerAdaptor.colorRes[it])
    }

    init {
        when(type) {
            0,1 -> conversationViewHolder = ConversationViewHolder(root, mContext)
            2,3 -> messageViewHolder = MessageViewHolder(mContext, root)
        }
    }

    @SuppressLint("SetTextI18n")
    fun onBind(resultItem: ResultItem) {
        item = resultItem
        val pos = absoluteAdapterPosition
        when(type) {
            0,1 -> {
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

                    if (type == 1) {
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
            2,3 -> {
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
            4 -> {
                val labelTextView: TextView = root.findViewById(R.id.label)
                if (item.categoryHeader == 42)
                    labelTextView.text = mContext.getString(R.string.from_contacts)
                else {
                    val label = item.categoryHeader - (if (item.categoryHeader > 9) 10 else 0)
                    val labelText = mContext.getString(labelText[label])
                    labelTextView.text = when {
                        item.categoryHeader > 9 ->
                            mContext.getString(R.string.messages_in_label, labelText)
                        else -> labelText
                    }
                }
            }
            5 -> {
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