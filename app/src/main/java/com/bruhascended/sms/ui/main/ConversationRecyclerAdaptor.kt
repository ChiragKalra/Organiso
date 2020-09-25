package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.provider.ContactsContract
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.QuickContactBadge
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.data.ContactsManager
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.ConversationComparator
import com.bruhascended.sms.dpMemoryCache
import com.bruhascended.sms.ml.displayTime
import com.bruhascended.sms.ui.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.sms.ui.ListSelectionManager
import com.squareup.picasso.Picasso

@SuppressLint("ResourceType")
class ConversationRecyclerAdaptor(
    private val mContext: Context
): SelectionRecyclerAdaptor<Conversation,
        ConversationRecyclerAdaptor.ConversationViewHolder>(ConversationComparator) {

    private var cm: ContactsManager = ContactsManager(mContext)
    private val contacts = cm.getContactsHashMap()
    private val picasso = Picasso.get()
    private var mSelectedItemsIds = SparseBooleanArray()
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE

    lateinit var selectionManager: ListSelectionManager<Conversation>

    private var colors: Array<Int> = arrayOf(
        ContextCompat.getColor(mContext, R.color.red),
        ContextCompat.getColor(mContext, R.color.blue),
        ContextCompat.getColor(mContext, R.color.purple),
        ContextCompat.getColor(mContext, R.color.green),
        ContextCompat.getColor(mContext, R.color.teal),
        ContextCompat.getColor(mContext, R.color.orange)
    )

    inner class ConversationViewHolder(val root: View): RecyclerView.ViewHolder(root) {
        val imageView: QuickContactBadge = root.findViewById(R.id.dp)
        val muteImage: ImageView = root.findViewById(R.id.mutedImage)
        val senderTextView: TextView = root.findViewById(R.id.sender)
        val messageTextView: TextView = root.findViewById(R.id.lastMessage)
        val timeTextView: TextView = root.findViewById(R.id.time)

        var currentVelocity = 0f

        val rotation: SpringAnimation = SpringAnimation(itemView, SpringAnimation.ROTATION)
            .setSpring(
                SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_HIGH_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_LOW)
            )
            .addUpdateListener { _, _, velocity ->
                currentVelocity = velocity
            }

        /**
         * A [SpringAnimation] for this RecyclerView item. This animation is used to bring the item back
         * after the over-scroll effect.
         */
        val translationY: SpringAnimation = SpringAnimation(itemView, SpringAnimation.TRANSLATION_Y)
            .setSpring(
                SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_LOW)
            )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        return ConversationViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.apply {
            val cur = getItem(position) ?: return
            val tp = mContext.obtainStyledAttributes(intArrayOf(
                R.attr.multiChoiceSelectorColor,
                android.R.attr.selectableItemBackground,
                R.attr.unreadTextColor
            ))
            val defaultBackground = tp.getDrawable(1)!!
            val selectedColor = tp.getColor(0, 0)
            val textColor = tp.getColor(2, 0)
            tp.recycle()

            root.apply {
                if (selectionManager.isSelected(position))
                    setBackgroundColor(selectedColor)
                else background = defaultBackground
                setOnClickListener{
                    val pos = absoluteAdapterPosition
                    if (selectionManager.isActive) {
                        selectionManager.toggleItem(pos)
                        if (selectionManager.isSelected(pos)) {
                            Handler(context.mainLooper).postDelayed({
                                (mContext as AppCompatActivity).runOnUiThread{
                                    if (selectionManager.isSelected(pos))
                                        setBackgroundColor(selectedColor)
                                }
                            }, 200)
                        } else {
                            background = defaultBackground
                        }
                    } else mContext.startActivity(
                        Intent(mContext, ConversationActivity::class.java)
                            .putExtra("ye", cur)
                    )
                }
                setOnLongClickListener {
                    val pos = absoluteAdapterPosition
                    selectionManager.toggleItem(pos)
                    if (selectionManager.isSelected(pos)) {
                        setBackgroundColor(selectedColor)
                    } else {
                        background = defaultBackground
                    }
                    true
                }
            }

            if (cur.name == null) cur.name = contacts[cur.sender]
            senderTextView.text = cur.name ?: cur.sender
            timeTextView.text = displayTime(cur.time, mContext)

            val str = if (cur.lastMMS) SpannableString("Media ${cur.lastSMS}")
            else SpannableString(cur.lastSMS)
            if (!cur.read) {
                str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
                str.setSpan(ForegroundColorSpan(textColor), 0, str.length, flag)
            }
            if (cur.lastMMS) str.apply {
                val color = mContext.getColor(R.color.colorAccent)
                setSpan(ForegroundColorSpan(color), 0, 6, flag)
            }
            messageTextView.text = str

            muteImage.visibility = if (cur.isMuted) View.VISIBLE else View.GONE
            imageView.apply {
                assignContactFromPhone(cur.sender, true)
                setMode(ContactsContract.QuickContact.MODE_LARGE)
                setBackgroundColor(colors[absoluteAdapterPosition % colors.size])

                if (cur.sender.first().isLetter()) {
                    setImageResource(R.drawable.ic_bot)
                    isEnabled = false
                } else if (cur.name != null) {
                    if (dpMemoryCache.containsKey(cur.sender)) {
                        val dp = dpMemoryCache[cur.sender]
                        if (dp != null) setImageBitmap(dp)
                        else setImageResource(R.drawable.ic_person)
                    } else {
                        setImageResource(R.drawable.ic_person)
                        Thread {
                            dpMemoryCache[cur.sender] = cm.retrieveContactPhoto(cur.sender)
                            val dp = dpMemoryCache[cur.sender]
                            (mContext as Activity).runOnUiThread {
                                if (dp != null) setImageBitmap(dp)
                            }
                        }.start()
                    }
                } else {
                    setImageResource(R.drawable.ic_person)
                }
            }
        }
    }
}