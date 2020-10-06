package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationComparator
import com.bruhascended.sms.ui.common.ListSelectionManager
import com.bruhascended.sms.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor

@SuppressLint("ResourceType")
class ConversationRecyclerAdaptor(
    private val mContext: Context
): SelectionRecyclerAdaptor<Conversation, ConversationViewHolder>(ConversationComparator) {

    companion object{
        val colorRes: Array<Int> = arrayOf(
            R.color.red, R.color.purple, R.color.blue, R.color.teal,
            R.color.green, R.color.yellow, R.color.orange
        )
    }

    private val colors: Array<Int> = Array(colorRes.size) {
        ContextCompat.getColor(mContext, colorRes[it])
    }

    lateinit var selectionManager: ListSelectionManager<Conversation>

    private fun onItemClickListener(it: ConversationViewHolder) {
        val pos = it.absoluteAdapterPosition
        if (selectionManager.isActive) {
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
        } else {
            val holder = it
            it.root.apply {
                setOnClickListener {  }
                postDelayed({setOnClickListener{onItemClickListener(holder)}}, 500)
            }
            mContext.startActivity(
                Intent(mContext, ConversationActivity::class.java)
                    .putExtra("ye", it.conversation)
            )
        }
    }

    var onItemLongClickListener: (ConversationViewHolder) -> Boolean = {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false),
            parent.context
        )
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.apply {
            imageView.setBackgroundColor(colors[absoluteAdapterPosition % colors.size])
            conversation = getItem(position) ?: return
            onBind()
            root.apply {
                stopBgAnim()
                if (::selectionManager.isInitialized &&
                    selectionManager.isRangeSelection(position)) {
                    rangeSelectionAnim()
                } else if (selectionManager.isSelected(absoluteAdapterPosition))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener { onItemClickListener(holder) }
                setOnLongClickListener { onItemLongClickListener(holder) }
            }
        }
    }
}