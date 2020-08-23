package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.moveTo
import com.bruhascended.sms.mainViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ConversationMultiChoiceModeListener(
    private val mContext: Context,
    private val listView: ListView,
    private val label: Int,
): AbsListView.MultiChoiceModeListener {

    private var rangeSelect = false
    private var previousSelected = -1
    private var actionMenu: Menu? = null
    private var editListAdapter = listView.adapter as ConversationListViewAdaptor

    @SuppressLint("InflateParams")
    private fun toggleRange(item: MenuItem): Boolean {
        val inf = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val iv = inf.inflate(R.layout.view_button_transition, null) as ImageView

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
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

    override fun onDestroyActionMode(mode: ActionMode) {
        editListAdapter.removeSelection()
        mainViewModel.selection.postValue(-1)
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        actionMenu = menu
        mode.menuInflater.inflate(R.menu.conversation_selection, menu)
        rangeSelect = false
        previousSelected = -1
        mainViewModel.selection.postValue(label)
        return true
    }

    @SuppressLint("InflateParams")
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
        return when (item.itemId) {
            R.id.action_select_range -> toggleRange(item)
            R.id.action_select_all -> {
                if (rangeSelect) toggleRange(actionMenu!!.findItem(R.id.action_select_range))
                for (i in 0 until editListAdapter.count) {
                    if (!listView.isItemChecked(i)) {
                        listView.setItemChecked(i, true)
                    }
                }
                true
            }
            R.id.action_delete -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to delete selected conversations?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        for (i in 0 until selected.size()) {
                            if (selected.valueAt(i)) {
                                val selectedItem: Conversation = editListAdapter.getItem(selected.keyAt(i))
                                moveTo(selectedItem, -1, mContext)
                            }
                        }

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
            R.id.action_block -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to block selected conversations?")
                    .setPositiveButton("Block") { dialog, _ ->
                        for (i in 0 until selected.size())
                            if (selected.valueAt(i))
                                moveTo(editListAdapter.getItem(selected.keyAt(i)), 5)
                        Toast.makeText(mContext,"Senders Blocked", Toast.LENGTH_LONG).show()
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
            R.id.action_report_spam -> {
                AlertDialog.Builder(mContext).setTitle("Do you want to report selected conversations?")
                    .setPositiveButton("Report") { dialog, _ ->
                        for (i in 0 until selected.size())
                            if (selected.valueAt(i))
                                moveTo(editListAdapter.getItem(selected.keyAt(i)), 4)
                        Toast.makeText(mContext, "Senders Reported Spam", Toast.LENGTH_LONG).show()
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
            R.id.action_move -> {
                val choices = Array(4){ its -> mContext.resources.getString(labelText[its])}
                var selection = label
                AlertDialog.Builder(mContext).setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select -> selection = select}
                    .setPositiveButton("Move") { dialog, _ ->
                        for (i in 0 until selected.size())
                            if (selected.valueAt(i))
                                moveTo(editListAdapter.getItem(selected.keyAt(i)), selection)
                        Toast.makeText(mContext, "Conversations Moved", Toast.LENGTH_LONG).show()
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
        mode.title = "${listView.checkedItemCount} selected"
    }
}