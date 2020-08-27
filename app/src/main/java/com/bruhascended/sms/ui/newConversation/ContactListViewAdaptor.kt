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
import com.bruhascended.sms.data.Contact
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.ui.dpMemoryCache

class ContactListViewAdaptor (
    private val contacts: Array<Contact>,
    private val mContext: Context
): RecyclerView.Adapter<ContactListViewAdaptor.MyViewHolder>() {

    private val colors: Array<Int> = arrayOf(
        ContextCompat.getColor(mContext, R.color.red),
        ContextCompat.getColor(mContext, R.color.blue),
        ContextCompat.getColor(mContext, R.color.purple),
        ContextCompat.getColor(mContext, R.color.green),
        ContextCompat.getColor(mContext, R.color.teal),
        ContextCompat.getColor(mContext, R.color.orange)
    )

    private val cm = ContactsManager(mContext)

    var onItemClick: ((Contact) -> Unit)? = null

    inner class MyViewHolder(view: View) :
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val contact: Contact = contacts[position]
        holder.name.text = contact.name
        holder.number.text = contact.number
        holder.dp.setBackgroundColor(colors[position % colors.size])

        val ad = contact.number
        if (dpMemoryCache.containsKey(ad)) {
            val dp = dpMemoryCache[ad]
            if (dp != null) holder.dp.setImageBitmap(dp)
            else holder.dp.setImageResource(R.drawable.ic_baseline_person_48)
        } else Thread {
            dpMemoryCache[ad] = cm.retrieveContactPhoto(ad)
            val dp = dpMemoryCache[ad]
            (mContext as Activity).runOnUiThread {
                if (dp != null) holder.dp.setImageBitmap(dp)
            }
        }.start()
    }

}
