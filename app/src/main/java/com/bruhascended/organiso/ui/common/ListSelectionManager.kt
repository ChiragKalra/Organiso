@file:Suppress("UNCHECKED_CAST")

package com.bruhascended.organiso.ui.common

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max
import kotlin.math.min


class ListSelectionManager<T: Any> (
    private val activity: AppCompatActivity,
    private val adaptor: SelectionRecyclerAdaptor<T, RecyclerView.ViewHolder>,
    private val listener: SelectionCallBack<T>,
){

    companion object {
        abstract class SelectionRecyclerAdaptor<T : Any, V : RecyclerView.ViewHolder>(
            c: DiffUtil.ItemCallback<T>
        ): PagingDataAdapter<T, V>(c) {
            fun getItemObject(pos: Int): T? {
                return super.getItem(pos)
            }
        }
    }

    interface SelectionCallBack<T>: ActionMode.Callback {
        fun onSingleItemSelected(item: T)
        fun onMultiItemSelected(list: List<T>)
    }

    private var actionMode: ActionMode? = null
    private var selected = arrayListOf<Int>()

    private var actionBarVisible = false
    private var rangeSelection = false
    private var previousSelection = -1

    private val dataObserver = object: RecyclerView.AdapterDataObserver() {
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            super.onItemRangeMoved(fromPosition, toPosition, itemCount)
            val changedSet = arrayListOf<Int>()
            val low = min(fromPosition+1, toPosition)
            val high = max(fromPosition+1, toPosition)
            for (i in selected) {
                if (i in fromPosition until fromPosition+itemCount) {
                    changedSet.add(i - fromPosition + toPosition)
                } else if (i in low .. high) {
                    if (fromPosition > toPosition) changedSet.add(i+itemCount)
                    else changedSet.add(i-itemCount)
                } else {
                    changedSet.add(i)
                }
            }
            selected = changedSet
            updateUi()
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            val changedSet = arrayListOf<Int>()
            for (i in selected) {
                if (i >= positionStart) changedSet.add(i+itemCount)
                else changedSet.add(i)
            }
            selected = changedSet
            updateUi()
            super.onItemRangeInserted(positionStart, itemCount)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            super.onItemRangeRemoved(positionStart, itemCount)
            val changedSet = arrayListOf<Int>()
            for (i in selected) {
                if (i !in positionStart until positionStart+itemCount) {
                    if (i > positionStart) changedSet.add(i - itemCount)
                    else changedSet.add(i)
                }
            }
            selected = changedSet
        }
    }

    private fun updateUi() {
        if (isActive && !actionBarVisible) {
            actionMode = activity.startSupportActionMode(listener)
            actionBarVisible = true
            adaptor.registerAdapterDataObserver(dataObserver)
        } else if (!isActive && actionBarVisible) {
            actionMode?.finish()
            actionMode = null
            actionBarVisible = false
            adaptor.unregisterAdapterDataObserver(dataObserver)
        }
        if (isActive) {
            if (selectedItems.size == 1) listener.onSingleItemSelected(selectedItems.first())
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
    val selectedItems: List<T> get() = arrayListOf<T>().apply {
        for (pos in selected) {
            add(adaptor.getItemObject(pos) ?: continue)
        }
    }

    fun isSelected(pos: Int) = selected.contains(pos)

    fun toggleRangeMode() {
        rangeSelection = !rangeSelection
        previousSelection = -1
    }

    fun toggleItem(pos: Int) {
        if (rangeSelection) {
            previousSelection = if (previousSelection == -1) pos
            else {
                val low = min(previousSelection, pos) + 1
                val high = max(previousSelection, pos) - 1
                for (i in low .. high) {
                    if (isSelected(i)) selected.remove(i)
                    else selected.add(i)
                }
                adaptor.notifyItemRangeChanged(low, high - low + 1)
                adaptor.notifyItemRangeChanged(previousSelection, 1)
                -1
            }
        }
        toggleSelection(pos)
        updateUi()
    }


    fun isRangeSelection(pos: Int) = pos == previousSelection

    fun close() {
        rangeSelection = false
        previousSelection = -1
        if (!isActive) return
        val copy = selected.toList()
        selected.clear()
        for (item in copy) {
            adaptor.notifyItemChanged(item)
        }
        updateUi()
    }
}