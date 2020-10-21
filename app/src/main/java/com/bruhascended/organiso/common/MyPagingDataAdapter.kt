package com.bruhascended.organiso.common

import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

abstract class MyPagingDataAdapter<T : Any, V : RecyclerView.ViewHolder>(
    c: DiffUtil.ItemCallback<T>
): PagingDataAdapter<T, V>(c) {
    fun getItemObject(pos: Int): T? {
        return super.getItem(pos)
    }
}