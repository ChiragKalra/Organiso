package com.bruhascended.sms.ui.listViewAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.R
import com.bruhascended.sms.data.Contact


class ContactListViewAdaptor (data: Array<Contact>):
    RecyclerView.Adapter<ContactListViewAdaptor.MyViewHolder>() {

    private val contacts: Array<Contact> = data

    var onItemClick: ((Contact) -> Unit)? = null

    inner class MyViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val number: TextView = view.findViewById(R.id.number)
        // val dp: ImageView = view.findViewById(R.id.dpImageView)
        init {
            view.setOnClickListener {
                onItemClick?.invoke(contacts[adapterPosition])
            }
        }
    }

    override fun getItemCount(): Int = contacts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val contact: Contact = contacts[position]
        holder.name.text = contact.name
        holder.number.text = contact.number
    }

}
