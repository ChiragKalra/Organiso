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

package com.bruhascended.sms.ui.newConversation

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.R
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.data.ContactsManager.Contact
import com.bruhascended.sms.ui.main.ConversationRecyclerAdaptor
import com.squareup.picasso.Picasso
import java.io.File

class ContactRecyclerAdaptor (
    private val mContext: Context,
    private val contacts: Array<Contact>
): RecyclerView.Adapter<ContactRecyclerAdaptor.ContactViewHolder>() {

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
                onItemClick?.invoke(contacts[layoutPosition])
            }
        }
    }

    override fun getItemCount(): Int = contacts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact: Contact = contacts[position]
        holder.name.text = contact.name
        holder.number.text = contact.number
        holder.dp.apply {
            setBackgroundColor(colors[position % colors.size])
            if (contact.dp == null) picasso.load(R.drawable.ic_person).into(this)
            else picasso.load(File(contact.dp)).into(this)
        }
    }

}
