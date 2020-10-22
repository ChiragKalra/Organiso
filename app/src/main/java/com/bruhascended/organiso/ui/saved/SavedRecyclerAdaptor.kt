package com.bruhascended.organiso.ui.saved

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import com.bruhascended.core.db.Saved
import com.bruhascended.core.db.SavedComparator
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.MyPagingDataAdapter

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

class SavedRecyclerAdaptor (
    private val mContext: Context
): MyPagingDataAdapter<Saved, SavedViewHolder>(SavedComparator) {

    private var onItemClickListener: ((SavedViewHolder) -> Unit)? = null

    fun setOnItemClickListener(listener: (SavedViewHolder) -> Unit) {
        onItemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedViewHolder {
        return SavedViewHolder(
            mContext,
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_saved_message, parent, false
            )
        )
    }

    @SuppressLint("ResourceType")
    override fun onBindViewHolder(holder: SavedViewHolder, position: Int) {
        holder.apply {
            message = getItem(position) ?: return
            onBind()
            root.setOnClickListener{
                onItemClickListener?.invoke(holder)
            }
            messageTextView.apply {
                setOnClickListener {
                    if (selectionStart == -1 && selectionEnd == -1) {
                        //This condition will satisfy only when it is not an auto linked text
                        //Fired only when you touch the part of the text that is not hyperlinked
                        onItemClickListener?.invoke(holder)
                    }
                }
            }
        }
    }
}
