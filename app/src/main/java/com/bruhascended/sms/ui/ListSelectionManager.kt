package com.bruhascended.sms.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.bruhascended.sms.ui.main.ConversationListViewAdaptor
import kotlin.math.max
import kotlin.math.min

class ListSelectionManager<T> (
    private val activity: AppCompatActivity,
    private val list: List<T>,
    private val adaptor: ConversationListViewAdaptor,
    private val listener: SelectionCallBack<T>,
){

    interface SelectionCallBack<T>: ActionMode.Callback {
        fun onSingleItemSelected(item: T)
        fun onMultiItemSelected(list: List<T>)
    }

    private var actionMode: ActionMode? = null
    private val selected = hashSetOf<Int>()

    private var actionBarVisible = false
    private var rangeSelection = false
    private var previousSelection = -1

    private fun updateUi() {
        if (isActive && !actionBarVisible) {
            actionMode = activity.startSupportActionMode(listener)
            actionBarVisible = true
        } else if (!isActive && actionBarVisible) {
            actionMode?.finish()
            actionMode = null
            actionBarVisible = false
        }
        if (isActive) {
            if (selected.size == 1) listener.onSingleItemSelected(selectedItems.first())
            else listener.onMultiItemSelected(selectedItems)
            actionMode?.title = selected.size.toString()
        }
    }

    private fun toggleSelection(pos: Int) {
        if (selected.contains(pos)) selected.remove(pos)
        else selected.add(pos)
    }

    val isRangeMode get() = rangeSelection
    val isActive get() = selected.size > 0
    val selectedItems: List<T> get() = arrayListOf<T>().apply { for (pos in selected) add(list[pos]) }

    fun isSelected(pos: Int) = selected.contains(pos)

    fun toggleRangeMode() {
        rangeSelection = !rangeSelection
        previousSelection = -1
    }

    fun toggleItem(pos: Int) {
        if (rangeSelection) {
            previousSelection = if (previousSelection == -1) pos
            else {
                val low = min(previousSelection+1, pos-1)
                val high = max(previousSelection+1, pos-1)
                for (i in low..high) {
                    if (isSelected(i)) selected.remove(i)
                    else selected.add(i)
                }
                adaptor.notifyItemRangeChanged(low, high-low+1)
                -1
            }
        }
        toggleSelection(pos)
        updateUi()
    }

    fun selectAll() {
        for (pos in list.indices) {
            if (!selected.contains(pos)) {
                selected.add(pos)
                adaptor.notifyItemChanged(pos)
            }
        }
        updateUi()
    }

    fun close() {
        if (!isActive) return
        val copy = selected.toList()
        selected.clear()
        for (item in copy) {
            adaptor.notifyItemChanged(item)
        }
        updateUi()
    }
}