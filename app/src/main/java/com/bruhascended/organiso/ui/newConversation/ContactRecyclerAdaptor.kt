package com.bruhascended.organiso.ui.newConversation

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.organiso.data.ContactsManager
import com.bruhascended.organiso.db.Contact
import com.bruhascended.organiso.db.ContactComparator
import com.bruhascended.organiso.ui.common.ListSelectionManager
import com.bruhascended.organiso.ui.main.ConversationRecyclerAdaptor
import com.bruhascended.organiso.ui.newConversation.ContactRecyclerAdaptor.ContactViewHolder
import com.squareup.picasso.Picasso
import java.io.File
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

class ContactRecyclerAdaptor (
    private val mContext: Context
): ListSelectionManager.SelectionRecyclerAdaptor<Contact, ContactViewHolder>(
    ContactComparator
){

    private var colors: Array<Int> = Array(ConversationRecyclerAdaptor.colorRes.size) {
        ContextCompat.getColor(mContext, ConversationRecyclerAdaptor.colorRes[it])
    }

    private val cm = ContactsManager(mContext)
    private val picasso = Picasso.get()

    var onItemClick: ((Contact) -> Unit)? = null

    inner class ContactViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val number: TextView = view.findViewById(R.id.number)
        val dp: ImageView = view.findViewById(R.id.dpImageView)

        init {
            view.setOnClickListener {
                onItemClick?.invoke(getItem(layoutPosition)!!)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact: Contact = getItem(position) ?: return
        holder.name.text = contact.name
        holder.number.text = contact.number
        val dp = File(mContext.filesDir, contact.number)
        holder.dp.apply {
            setBackgroundColor(colors[abs(contact.hashCode()) % colors.size])
            if (dp.exists()) picasso.load(dp).into(this)
            else setImageResource(R.drawable.ic_person)
        }
    }

}
