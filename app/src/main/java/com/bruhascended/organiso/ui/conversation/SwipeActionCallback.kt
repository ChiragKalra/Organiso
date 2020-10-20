package com.bruhascended.organiso.ui.conversation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.organiso.settings.InterfaceFragment.Companion.ACTION_BLOCK
import com.bruhascended.organiso.settings.InterfaceFragment.Companion.ACTION_DELETE
import com.bruhascended.organiso.settings.InterfaceFragment.Companion.ACTION_REPORT
import com.bruhascended.organiso.ui.main.ConversationViewHolder
import kotlin.math.abs
import kotlin.math.roundToInt


class SwipeActionCallback(
    private val mContext: Context,
    private val cancelCallBack: (RecyclerView.ViewHolder?) -> Unit,
    private val leftAction: String,
    private val rightAction: String,
): ItemTouchHelper.SimpleCallback(
    0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    private val leftIcon =
        ResourcesCompat.getDrawable(
            mContext.resources, getDrawableId(leftAction), mContext.theme
        )!!.toBitmap()

    private val rightIcon =
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

    private fun Int.toPx() =
        (this * mContext.resources.displayMetrics.density).roundToInt()

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
            val itemView: View = viewHolder.itemView
            val p = Paint()
            if (dX > 0) {
                p.color = mContext.getColor(getColorId(rightAction))

                c.drawRect(
                    itemView.left.toFloat(),
                    itemView.top.toFloat(),
                    dX,
                    itemView.bottom.toFloat(),
                    p
                )
                c.drawBitmap(
                    rightIcon,
                    itemView.left.toFloat() + 16.toPx(),
                    itemView.top.toFloat() + (itemView.bottom.toFloat() -
                            itemView.top.toFloat() - rightIcon.height) / 2,
                    p
                )
            } else if (dX < 0) {
                p.color = mContext.getColor(getColorId(leftAction))

                c.drawRect(
                    itemView.right.toFloat() + dX,
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat(),
                    p
                )

                c.drawBitmap(
                    leftIcon,
                    itemView.right.toFloat() - 16.toPx() - leftIcon.width,
                    itemView.top.toFloat() + (itemView.bottom.toFloat() -
                            itemView.top.toFloat() - leftIcon.height) / 2,
                    p
                )
            }

            val alpha = 1 - abs(dX) / viewHolder.itemView.width.toFloat()
            viewHolder.itemView.alpha = alpha
            viewHolder.itemView.translationX = dX
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }
}