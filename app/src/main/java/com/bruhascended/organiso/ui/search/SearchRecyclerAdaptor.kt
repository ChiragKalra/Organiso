package com.bruhascended.organiso.ui.search

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.*
import com.bruhascended.organiso.SearchActivity.Companion.TYPE_FOOTER
import com.bruhascended.organiso.SearchActivity.Companion.TYPE_HEADER
import com.bruhascended.organiso.SearchActivity.Companion.TYPE_MESSAGE_RECEIVED
import com.bruhascended.organiso.SearchActivity.Companion.TYPE_MESSAGE_SENT
import com.bruhascended.core.db.Conversation
import com.bruhascended.organiso.ui.search.SearchResultViewHolder.ResultItem

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


class SearchRecyclerAdaptor(
    private val mContext: Context,
    private val items: ArrayList<ResultItem>
) : RecyclerView.Adapter<SearchResultViewHolder>() {

    var doOnConversationClick: (Conversation) -> Unit = {}
    var doOnMessageClick: (Pair<Long, Conversation>) -> Unit = {}
    lateinit var doOnLoaded: () -> Unit

    var isLoaded = false

    var searchKey = ""

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        return SearchResultViewHolder(
            mContext, viewType, this,
            LayoutInflater.from(parent.context).inflate(
                when (viewType) {
                    TYPE_MESSAGE_SENT -> R.layout.item_message_out
                    TYPE_MESSAGE_RECEIVED -> R.layout.item_message
                    TYPE_HEADER -> R.layout.item_search_category
                    TYPE_FOOTER -> R.layout.item_search_footer
                    else -> R.layout.item_conversation
                }, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    fun addItems(newItems: List<ResultItem>) {
        items.apply {
            val p = removeAt(lastIndex)
            addAll(newItems)
            add(p)
        }
        notifyItemRangeInserted(itemCount - 2, newItems.size)
    }

    fun refresh() {
        isLoaded = false
        items.apply {
            val footer = removeAt(lastIndex)
            clear()
            add(footer)
        }
        notifyDataSetChanged()
    }
}