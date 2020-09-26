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
import com.bruhascended.sms.data.ContactsManager
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
            R.color.red, R.color.blue, R.color.purple, R.color.green,
            R.color.teal, R.color.orange, R.color.yellow
        )
    }

    class ConversationSharedResources(val mContext: Context) {
        val colors: Array<Int> = Array(colorRes.size) {
            ContextCompat.getColor(mContext, colorRes[it])
        }

        val cm = ContactsManager(mContext)
        val contacts = cm.getContactsHashMap()
    }

    private val sharedResources = ConversationSharedResources(mContext)
    lateinit var selectionManager: ListSelectionManager<Conversation>


    var onItemClickListener: (ConversationViewHolder) -> Unit = {
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
        } else mContext.startActivity(
            Intent(mContext, ConversationActivity::class.java)
                .putExtra("ye", it.conversation)
        )
    }

    var onItemLongClickListener: (ConversationViewHolder) -> Boolean = {
        val pos = it.absoluteAdapterPosition
        selectionManager.toggleItem(pos)
        if (selectionManager.isSelected(pos)) {
            it.root.setBackgroundColor(it.selectedColor)
        } else {
            it.root.background = it.defaultBackground
        }
        true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false),
            sharedResources
        )
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.apply {
            conversation = getItem(position) ?: return
            onBind()
            imageView.setBackgroundColor(
                sharedResources.colors[absoluteAdapterPosition % sharedResources.colors.size]
            )
            root.apply {
                if (selectionManager.isSelected(absoluteAdapterPosition))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener { onItemClickListener(holder) }
                setOnLongClickListener { onItemLongClickListener(holder) }
            }
        }
    }
}