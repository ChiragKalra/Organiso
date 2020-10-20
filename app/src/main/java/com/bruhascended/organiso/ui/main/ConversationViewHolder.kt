package com.bruhascended.organiso.ui.main

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Conversation
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.DateTimeProvider
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.squareup.picasso.Picasso
import java.io.File

@SuppressLint("ResourceType")
class ConversationViewHolder(
    val root: View,
    private val mContext: Context,
) : ScrollEffectFactory.ScrollEffectViewHolder(root) {

    private val picasso = Picasso.get()
    private val dtp = DateTimeProvider(mContext)
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private var backgroundAnimator: ValueAnimator? = null
    private val muteImage: ImageView = root.findViewById(R.id.mutedImage)

    val imageView: QuickContactBadge = root.findViewById(R.id.dp)
    val senderTextView: TextView = root.findViewById(R.id.sender)
    val messageTextView: TextView = root.findViewById(R.id.lastMessage)
    val timeTextView: TextView = root.findViewById(R.id.time)

    val isInitialised
        get() = ::conversation.isInitialized

    lateinit var conversation: Conversation

    var defaultBackground: Drawable
    var selectedColor = 0
    var backgroundColor = 0
    var textColor = 0

    init {
        val tp = mContext.obtainStyledAttributes(
            intArrayOf(
                R.attr.multiChoiceSelectorColor,
                android.R.attr.selectableItemBackground,
                R.attr.unreadTextColor,
                R.attr.backgroundColor
            )
        )
        defaultBackground = tp.getDrawable(1)!!
        selectedColor = tp.getColor(0, 0)
        backgroundColor = tp.getColor(3, 0)
        textColor = tp.getColor(2, 0)
        tp.recycle()
    }

    private fun showDisplayPicture() {
        imageView.apply {
            isEnabled = true
            assignContactFromPhone(conversation.clean, true)
            setMode(ContactsContract.QuickContact.MODE_LARGE)
            val dp = File(mContext.filesDir, conversation.clean)
            when {
                conversation.clean.first().isLetter() -> {
                    setImageResource(R.drawable.ic_bot)
                    isEnabled = false
                }
                dp.exists() -> picasso.load(dp).into(this)
                else -> setImageResource(R.drawable.ic_person)
            }
        }
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context, intent: Intent) {
                if (intent.getStringExtra(EXTRA_SENDER) == conversation.clean) {
                    val dp = File(mContext.filesDir, conversation.clean)
                    picasso.load(dp).into(imageView)
                }
            }
        }, IntentFilter(ACTION_UPDATE_DP))
    }

    fun onBind() {
        senderTextView.text = conversation.name ?: conversation.address
        timeTextView.text = dtp.getCondensed(conversation.time)

        val str = if (conversation.lastMMS)
            SpannableString(mContext.getString(R.string.media_message, conversation.lastSMS))
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