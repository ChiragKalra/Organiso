package com.bruhascended.organiso.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.method.ScrollingMovementMethod
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.bruhascended.organiso.R


class FloatHint(
    private val mContext: Context,
    private val mText: String
) {

    private val viewResource: Int = R.layout.layout_popup
    private var mWindowManager: WindowManager = mContext
        .getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var mWindow: PopupWindow = PopupWindow(mContext)
    private var mTextView: TextView
    private var mImageView: ImageView
    private var mView: View

    init {
        val layoutInflater = mContext
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mView = layoutInflater.inflate(viewResource, null)
        mWindow.contentView = mView
        mTextView = mView.findViewById(R.id.text)
        mImageView = mView.findViewById(R.id.arrow_up)
        mTextView.movementMethod = ScrollingMovementMethod.getInstance()
        mTextView.isSelected = true
    }

    private fun preShow(anchor: View) {
        mWindow.apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = (anchor.height * 1.05).toInt()
            isTouchable = false
            isFocusable = false
            isOutsideTouchable = false
            contentView = mView
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        mTextView.text = mText
    }

    fun show(anchor: View) {
        preShow(anchor)
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        mView.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        mWindow.showAtLocation(
            anchor,
            Gravity.NO_GRAVITY,
            location[0] + anchor.width,
            location[1] - (anchor.height*0.05).toInt()
        )
        mView.animation = AnimationUtils.loadAnimation(
            mContext,
            R.anim.float_hint
        )
    }

    fun dismiss() {
        mWindow.dismiss()
    }
}
