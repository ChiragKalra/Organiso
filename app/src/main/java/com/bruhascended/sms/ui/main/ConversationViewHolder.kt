package com.bruhascended.sms.ui.main

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.provider.ContactsContract
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ImageView
import android.widget.QuickContactBadge
import android.widget.TextView
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ml.displayTime
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import com.squareup.picasso.Picasso
import java.io.File

@SuppressLint("ResourceType")
class ConversationViewHolder(
    val root: View,
    private val mContext: Context,
) : ScrollEffectFactory.ScrollEffectViewHolder(root) {

    private val picasso = Picasso.get()
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private var backgroundAnimator: ValueAnimator? = null
    private val muteImage: ImageView = root.findViewById(R.id.mutedImage)

    val imageView: QuickContactBadge = root.findViewById(R.id.dp)
    val senderTextView: TextView = root.findViewById(R.id.sender)
    val messageTextView: TextView = root.findViewById(R.id.lastMessage)
    val timeTextView: TextView = root.findViewById(R.id.time)

    lateinit var conversation: Conversation

    var defaultBackground: Drawable
    var selectedColor = 0
    var backgroundColor = 0
    var textColor = 0

    init {
        val tp = mContext.obtainStyledAttributes(intArrayOf(
            R.attr.multiChoiceSelectorColor,
            android.R.attr.selectableItemBackground,
            R.attr.unreadTextColor,
            R.attr.backgroundColor
        ))
        defaultBackground = tp.getDrawable(1)!!
        selectedColor = tp.getColor(0, 0)
        backgroundColor = tp.getColor(3, 0)
        textColor = tp.getColor(2, 0)
        tp.recycle()
    }

    private fun showDisplayPicture() {
        imageView.apply {
            isEnabled = true
            assignContactFromPhone(conversation.sender, true)
            setMode(ContactsContract.QuickContact.MODE_LARGE)
            val dp = File(mContext.filesDir, conversation.sender)
            when {
                conversation.sender.first().isLetter() -> {
                    setImageResource(R.drawable.ic_bot)
                    isEnabled = false
                }
                dp.exists() -> picasso.load(dp).into(this)
                else -> setImageResource(R.drawable.ic_person)
            }
        }
    }

    fun onBind() {
        senderTextView.text = conversation.name ?: conversation.sender
        timeTextView.text = displayTime(conversation.time, mContext)

        val str = if (conversation.lastMMS)
            SpannableString("Media ${conversation.lastSMS}")
        else SpannableString(conversation.lastSMS)
        if (!conversation.read) {
            str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
            str.setSpan(ForegroundColorSpan(textColor), 0, str.length, flag)
        }
        if (conversation.lastMMS) str.apply {
            val color = mContext.getColor(R.color.colorAccent)
            setSpan(ForegroundColorSpan(color), 0, 6, flag)
        }
        messageTextView.text = str
        muteImage.visibility = if (conversation.isMuted) View.VISIBLE else View.GONE

        showDisplayPicture()
    }


    fun rangeSelectionAnim() {
        backgroundAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            selectedColor,
            backgroundColor
        ).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                root.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    fun stopBgAnim() {
        backgroundAnimator?.cancel()
    }

}