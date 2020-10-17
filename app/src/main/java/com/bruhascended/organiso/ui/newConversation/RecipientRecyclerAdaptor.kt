package com.bruhascended.organiso.ui.newConversation

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.organiso.db.Contact
import com.bruhascended.organiso.ui.main.ConversationRecyclerAdaptor
import kotlin.math.abs

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

class RecipientRecyclerAdaptor (
    private val mContext: Context,
    private val contacts: ArrayList<Contact>
): RecyclerView.Adapter<RecipientRecyclerAdaptor.RecipientViewHolder>() {

    private var colors: Array<Int> = Array(ConversationRecyclerAdaptor.colorRes.size) {
        ContextCompat.getColor(mContext, ConversationRecyclerAdaptor.colorRes[it])
    }

    var onItemClick: ((Contact) -> Unit)? = null

    inner class RecipientViewHolder(val root: View) :
        RecyclerView.ViewHolder(root) {
        val name: TextView = root.findViewById(R.id.name)
        val remove: ImageButton = root.findViewById(R.id.remove)
    }

    override fun getItemCount(): Int = contacts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipientViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_send_address, parent, false)
        return RecipientViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecipientViewHolder, position: Int) {
        val contact: Contact = contacts[position]
        holder.apply {
            name.text = if (contact.name.isBlank()) contact.number else contact.name
            root.backgroundTintList = ColorStateList.valueOf(colors[abs(contact.hashCode()) % colors.size])
            remove.setOnClickListener {
                contacts.removeAt(absoluteAdapterPosition)
                notifyDataSetChanged()
            }
            root.setOnClickListener {
                onItemClick?.invoke(contacts[layoutPosition])
                contacts.removeAt(absoluteAdapterPosition)
                notifyDataSetChanged()
            }
        }
    }
}
