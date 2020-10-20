package com.bruhascended.organiso.ui.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.ui.conversation.ConversationMenuOptions
import java.lang.Integer.min
import kotlin.math.roundToInt

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

class SwipeActionCallback(
    private val mContext: Context,
    private val cancelCallBack: (RecyclerView.ViewHolder?) -> Unit,
    private val leftAction: String,
    private val rightAction: String,
    private val swipeStrength: Int,
): ItemTouchHelper.SimpleCallback(
    0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {
    private val margin = 16.toPx()

    private val rightIcon =
        ResourcesCompat.getDrawable(
            mContext.resources, getDrawableId(leftAction), mContext.theme
        )!!.toBitmap()

    private val leftIcon =
        ResourcesCompat.getDrawable(
            mContext.resources, getDrawableId(rightAction), mContext.theme
        )!!.toBitmap()

    private fun getActionId(actionString: String) = when (actionString) {
        ACTION_BLOCK -> R.id.action_block
        ACTION_DELETE -> R.id.action_delete
        ACTION_REPORT -> R.id.action_report_spam
        else -> R.id.action_move
    }

    private fun getDrawableId(actionString: String) = when (actionString) {
        ACTION_BLOCK -> R.drawable.ic_block
        ACTION_DELETE -> R.drawable.ic_delete
        ACTION_REPORT -> R.drawable.ic_report
        else -> R.drawable.ic_move
    }

    private fun getColorId(actionString: String) = when (actionString) {
        ACTION_BLOCK -> R.color.block
        ACTION_DELETE -> R.color.delete
        ACTION_REPORT -> R.color.report
        else -> R.color.move
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable) {
            return bitmap
        }
        val bitmap =
            Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    private fun Int.toPx() = this * mContext.resources.displayMetrics.density

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (viewHolder is ConversationViewHolder && viewHolder.isInitialised) {
            super.getSwipeDirs(recyclerView, viewHolder)
        } else {
            0
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, swipeDir: Int) {
        viewHolder as ConversationViewHolder
        val optionMenu = ConversationMenuOptions(
            mContext, viewHolder.conversation,
            itemViewHolder = viewHolder, cancelCallBack = cancelCallBack
        )
        optionMenu.onOptionsItemSelected(
            itemId = getActionId(
                if (swipeDir == ItemTouchHelper.LEFT) leftAction else rightAction
            )
        )
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * swipeStrength / 2f
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        viewHolder as ConversationViewHolder
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val p = Paint()
            viewHolder.itemView.apply {
                if (dX > 0) {
                    p.color = mContext.getColor(getColorId(rightAction))
                    c.drawRect(
                        left.toFloat(),
                        top.toFloat(),
                        dX,
                        bottom.toFloat(),
                        p
                    )

                    val width = min(leftIcon.width, (dX - margin).roundToInt())
                    if (width > 0) {
                        val cropped = Bitmap.createBitmap(
                            leftIcon,
                            0,
                            0,
                            width,
                            leftIcon.height
                        )

                        c.drawBitmap(
                            cropped,
                            left + margin,
                            top + (bottom - top - cropped.height) / 2f,
                            p
                        )
                    }
                } else if (dX < 0) {
                    p.color = mContext.getColor(getColorId(leftAction))

                    c.drawRect(
                        right + dX,
                        top.toFloat(),
                        right.toFloat(),
                        bottom.toFloat(),
                        p
                    )
                    val width = min(rightIcon.width, (-margin - dX).roundToInt())
                    if (width > 0) {
                        val cropped = Bitmap.createBitmap(
                            rightIcon,
                            rightIcon.width - width,
                            0,
                            width,
                            rightIcon.height
                        )

                        c.drawBitmap(
                            cropped,
                            right - margin - cropped.width,
                            top + (bottom - top - cropped.height) / 2f,
                            p
                        )
                    }
                }

                alpha = 1 - dX/ width
                translationX = dX
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}