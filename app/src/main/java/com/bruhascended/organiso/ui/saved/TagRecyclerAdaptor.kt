package com.bruhascended.organiso.ui.saved

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R

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

class TagRecyclerAdaptor (
    private val mContext: Context
): PagingDataAdapter<String, TagRecyclerAdaptor.TagViewHolder>(
    object: DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(a: String, b: String) = a == b
        override fun areContentsTheSame(a: String, b: String) = a == b
    }
) {

    private var onItemClick: ((String) -> Unit)? = null

    inner class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: Button = view.findViewById(R.id.tag)

        init {
            name.setOnClickListener {
                onItemClick?.invoke(getItem(layoutPosition) ?: return@setOnClickListener)
            }
        }
    }

    fun setOnClickListener(listener: (String) -> Unit) {
        onItemClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_tag, parent, false)
        return TagViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.name.text = getItem(position) ?: return
    }

}
