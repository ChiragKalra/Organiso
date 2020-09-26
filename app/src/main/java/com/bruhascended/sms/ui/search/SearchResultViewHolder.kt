package com.bruhascended.sms.ui.search

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bruhascended.sms.R
import com.bruhascended.sms.data.SMSManager.Companion.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import com.bruhascended.sms.ui.conversastion.MessageViewHolder
import com.bruhascended.sms.ui.main.ConversationRecyclerAdaptor
import com.bruhascended.sms.ui.main.ConversationViewHolder


class SearchResultViewHolder(
    val mContext: Context,
    val type: Int,
    private val mAdaptor: SearchRecyclerAdaptor,
    val root: View,
    private val sharedResources: ConversationRecyclerAdaptor.ConversationSharedResources
): ScrollEffectFactory.ScrollEffectViewHolder(root) {

    data class ResultItem(
        val type: Int,
        val categoryHeader: Int = -1,
        val conversation: Conversation? = null,
        val message: Message? = null
    )

    lateinit var item: ResultItem
    private lateinit var conversationViewHolder: ConversationViewHolder
    private lateinit var messageViewHolder: MessageViewHolder


    init {
        when(type) {
            0,1 -> conversationViewHolder = ConversationViewHolder(root, sharedResources)
            2,3 -> messageViewHolder = MessageViewHolder(mContext, mAdaptor.searchKey, root)
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
                        sharedResources.colors[pos % sharedResources.colors.size]
                    )
                    root.apply {
                        background = defaultBackground
                        setOnClickListener { mAdaptor.doOnConversationClick(conversationViewHolder) }
                        setOnLongClickListener { false }
                    }

                    if (type == 1) {
                        senderTextView.apply {
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
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
                    message = item.message!!
                    onBind()
                    root.apply {
                        background = defaultBackground
                        setOnClickListener { mAdaptor.doOnMessageClick(messageViewHolder) }
                        setOnLongClickListener { false }
                    }
                }
            }
            4 -> {
                val labelTextView: TextView = root.findViewById(R.id.label)
                val label = item.categoryHeader - (if (item.categoryHeader > 9) 10 else 0)
                val labelText = mContext.getString(labelText[label])
                labelTextView.text = if (item.categoryHeader>9)
                    "Messages in $labelText" else labelText
            }
        }

    }
}