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
import com.bruhascended.core.constants.ACTION_UPDATE_DP
import com.bruhascended.core.constants.EXTRA_NUMBER
import com.bruhascended.core.data.ContactsProvider
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.core.db.Conversation
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.DateTimeProvider
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.squareup.picasso.Picasso
import java.io.File

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

@SuppressLint("ResourceType")
class ConversationViewHolder(
    val root: View,
    private val mContext: Context,
) : ScrollEffectFactory.ScrollEffectViewHolder(root) {

    private val picasso = Picasso.get()
    private val dtp = DateTimeProvider(mContext)
    private val mContactsProvider = ContactsProvider(mContext)
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private var backgroundAnimator: ValueAnimator? = null
    private val muteImage: ImageView = root.findViewById(R.id.mutedImage)
    private val mainDaos = MainDaoProvider(mContext).getMainDaos()

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
    private var mediaTextColor = 0

    init {
        val tp = mContext.obtainStyledAttributes(
            intArrayOf(
                R.attr.multiChoiceSelectorColor,
                android.R.attr.selectableItemBackground,
                R.attr.unreadTextColor,
                R.attr.backgroundColor,
                R.attr.headerTextColor,
            )
        )
        defaultBackground = tp.getDrawable(1)!!
        selectedColor = tp.getColor(0, 0)
        backgroundColor = tp.getColor(3, 0)
        mediaTextColor = tp.getColor(4, 0)
        textColor = tp.getColor(2, 0)
        tp.recycle()
    }

    private fun showDisplayPicture() {
        imageView.apply {
            isEnabled = true
            assignContactFromPhone(conversation.number, true)
            setMode(ContactsContract.QuickContact.MODE_LARGE)
            val dp = File(mContext.filesDir, conversation.number)
            when {
                conversation.isBot -> {
                    setImageResource(R.drawable.ic_bot)
                    isEnabled = false
                }
                dp.exists() -> picasso.load(dp).into(this)
                else -> setImageResource(R.drawable.ic_person)
            }
        }
        mContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(p0: Context, intent: Intent) {
                if (intent.getStringExtra(EXTRA_NUMBER) == conversation.number) {
                    val dp = File(mContext.filesDir, conversation.number)
                    picasso.load(dp).into(imageView)
                }
            }
        }, IntentFilter(ACTION_UPDATE_DP))
    }

    fun onBind() {
        val mNumber = conversation.number
        senderTextView.text = mNumber
        val live = mContactsProvider.getLive(mNumber)
        if (!live.hasActiveObservers()) {
            live.observeForever {
                if (mNumber == conversation.number && it != null) {
                    senderTextView.text = it
                }
            }
        }
        timeTextView.text = dtp.getCondensed(conversation.time)
        muteImage.visibility = if (conversation.isMuted) View.VISIBLE else View.GONE

        MessageDbFactory(mContext).of(mNumber).apply {
            manager().loadLastSync().also {
                if (mNumber != conversation.number) return@also
                if (it == null) {
                    if (conversation.isInDb) {
                        mainDaos[conversation.label].delete(conversation)
                    }
                    return@also
                }
                val str = if (it.hasMedia)
                    SpannableString(
                        mContext.getString(
                            R.string.media_message,
                            it.text.replace('\n',' ')
                        )
                    )
                else SpannableString(it.text.replace('\n',' '))
                if (!conversation.read) {
                    str.setSpan(StyleSpan(Typeface.BOLD), 0, str.length, flag)
                    str.setSpan(ForegroundColorSpan(textColor), 0, str.length, flag)
                }
                if (it.hasMedia) str.apply {
                    setSpan(ForegroundColorSpan(mediaTextColor), 0, 6, flag)
                }
                messageTextView.text = str
            }
            close()
        }

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