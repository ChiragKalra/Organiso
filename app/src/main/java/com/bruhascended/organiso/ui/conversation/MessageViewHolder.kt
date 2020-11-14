package com.bruhascended.organiso.ui.conversation

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.View.VISIBLE
import android.widget.TextView
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Message
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.MediaViewHolder
import java.util.*

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
class MessageViewHolder(
    private val mContext: Context,
    root: View,
) : MediaViewHolder(mContext, root) {

    private var backgroundAnimator: ValueAnimator? = null
    private val flag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
    private val highlightColor = mContext.getColor(R.color.textHighLight)
    private val timeTextView: TextView = root.findViewById(R.id.time)
    private val statusTextView: TextView? = try {
        root.findViewById(R.id.status)
    } catch (e: Exception) {
        null
    }

    lateinit var message: Message
    var searchKey = ""

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

    override fun getUid(): Long = message.time
    override fun getDataPath(): String = message.path!!


    override fun hideMedia() {
        super.hideMedia()
        if (message.type == 1) content.setBackgroundResource(R.drawable.bg_message)
        else content.setBackgroundResource(R.drawable.bg_message_out)
    }



    fun onBind(retryEnabled: Boolean = false) {
        hideMedia()

        messageTextView.text = if (!searchKey.isBlank()) SpannableString(message.text).apply {
            val regex = Regex("\\b${searchKey}")
            val matches = regex.findAll(message.text.toLowerCase(Locale.ROOT))
            for (match in matches) {
                val index = match.range
                setSpan(BackgroundColorSpan(highlightColor), index.first, index.last+1, flag)
            }
        } else message.text
        timeTextView.text = dtp.getFull(message.time)
        timeTextView.alpha = 0f

        if (message.type != MESSAGE_TYPE_INBOX) {
            statusTextView!!.visibility = VISIBLE
            statusTextView.setTextColor(textColor)
            statusTextView.text =  when {
                message.delivered -> mContext.getString(R.string.delivered)
                message.type == MESSAGE_TYPE_SENT -> mContext.getString(R.string.sent)
                message.type == MESSAGE_TYPE_QUEUED -> mContext.getString(R.string.queued)
                message.type == MESSAGE_TYPE_DRAFT -> {
                    statusTextView.setTextColor(mContext.getColor(R.color.blue))
                    if (retryEnabled) mContext.getString(R.string.drafted_edit)
                    else mContext.getString(R.string.drafted)
                }
                else -> {
                    statusTextView.setTextColor(mContext.getColor(R.color.red))
                    if (retryEnabled) mContext.getString(R.string.failed_retry)
                    else mContext.getString(R.string.failed)
                }
            }
        }

        if (message.path != null) {
            showMedia()
        }

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

    fun showTime() {
        timeTextView.apply {
            if (alpha != 0f) return
            postOnAnimation {
                animate().alpha(1f).setDuration(700).start()
                postDelayed( {
                    animate().alpha(0f).setDuration(700).start()
                }, 3700)
            }
        }
    }

}