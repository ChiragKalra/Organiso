package com.bruhascended.sms.ui.conversastion

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.SparseBooleanArray
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.db.MessageDao
import com.bruhascended.sms.db.moveTo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MessageMultiChoiceModeListener(
    private val mContext: Context,
    private val listView: ListView,
    private val mdb: MessageDao,
    private val conversation: Conversation,
): AbsListView.MultiChoiceModeListener {

    private var rangeSelect = false
    private var previousSelected = -1
    private var editListAdapter = listView.adapter as MessageListViewAdaptor

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        editListAdapter.removeSelection()
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.message_selection, menu)
        rangeSelect = false
        previousSelected = -1
        return true
    }

    @SuppressLint("InflateParams")
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to delete selected messages?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        Thread {
                            val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
                            for (i in 0 until selected.size()) {
                                if (selected.valueAt(i)) {
                                    val selectedItem: Message = editListAdapter.getItem(selected.keyAt(i))
                                    mdb.delete(selectedItem)
                                }
                            }
                            if (listView.checkedItemCount == editListAdapter.count) {
                                moveTo(conversation, -1, mContext)
                            }
                        }.start()
                        Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                        mode.finish()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        mode.finish()
                        dialog.dismiss()
                    }
                    .create().show()
                true
            }
            R.id.action_select_range -> {
                val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val iv = inflater.inflate(R.layout.view_button_transition, null) as ImageView

                if (rangeSelect) iv.setImageResource(R.drawable.range_to_single)
                else iv.setImageResource(R.drawable.single_to_range)
                item.actionView = iv
                (iv.drawable as AnimatedVectorDrawable).start()


                rangeSelect = !rangeSelect
                if (rangeSelect) previousSelected = -1

                GlobalScope.launch {
                    delay(300)
                    (mContext as Activity).runOnUiThread {
                        if (!rangeSelect) {
                            item.setIcon(R.drawable.ic_single)
                        } else {
                            item.setIcon(R.drawable.ic_range)
                        }
                        item.actionView = null
                    }
                }
                true
            }
            R.id.action_copy -> {
                val clipboard: ClipboardManager =
                    mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
                val sb = StringBuilder()
                for (i in 0 until selected.size()) {
                    if (selected.valueAt(i)) {
                        val selectedItem: Message = editListAdapter.getItem(selected.keyAt(i))
                        sb.append(selectedItem.text).append('\n')
                    }
                }
                val clip: ClipData = ClipData.newPlainText("none", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(mContext, "Copied", Toast.LENGTH_LONG).show()
                mode.finish()
                true
            }
            else -> false
        }
    }

    override fun onItemCheckedStateChanged(
        mode: ActionMode, position: Int, id: Long, checked: Boolean
    ) {
        if (rangeSelect) {
            previousSelected = if (previousSelected == -1) {
                position
            } else {
                val low = Integer.min(previousSelected, position) + 1
                val high = Integer.max(previousSelected, position) - 1
                for (i in low..high) {
                    listView.setItemChecked(i, !listView.isItemChecked(i))
                    editListAdapter.toggleSelection(i)
                }
                -1
            }
        }
        editListAdapter.toggleSelection(position)
        mode.title = listView.checkedItemCount.toString()
    }
}