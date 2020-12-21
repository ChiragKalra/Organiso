package com.bruhascended.organiso.settings.categories

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.core.constants.*
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

class RecyclerViewAdapter(
    private val mContext: Context,
    val data: ArrayList<Int>,
    private val mStartDragListener: StartDragListener,
    val customLabels: Array<String>
): RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder>(),
    ItemMoveCallback.ItemTouchHelperContract
{

    interface StartDragListener {
        fun requestDrag(viewHolder: RecyclerView.ViewHolder)
    }

    inner class MyViewHolder(val rowView: View): RecyclerView.ViewHolder(rowView) {
        val editText: EditText = rowView.findViewById(R.id.name)
        val dragButton: ImageButton = rowView.findViewById(R.id.drag)
        val clearButton: ImageButton = rowView.findViewById(R.id.clearText)
        val hideButton: ImageButton = rowView.findViewById(R.id.hideButton)
        val label: TextView = rowView.findViewById(R.id.label)
        val labelEditText: TextView = rowView.findViewById(R.id.nameLabel)
        var labelInt: Int = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView: View = inflater.inflate(R.layout.item_category, parent, false)
        return MyViewHolder(itemView)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val category = data[position] == CATEGORY_HIDDEN || data[position] == CATEGORY_VISIBLE
        val labelArr = mContext.resources.getStringArray(R.array.labels)
        val string = when {
            data[position] == CATEGORY_HIDDEN -> mContext.getString(R.string.hidden_category)
            data[position] == CATEGORY_VISIBLE -> mContext.getString(R.string.visible_category)
            else -> labelArr[data[position]]
        }

        holder.apply {
            labelInt = data[position]

            if (category) {
                label.text = string
                label.visibility = View.VISIBLE
                dragButton.visibility = View.GONE
                editText.visibility = View.GONE
                hideButton.visibility = View.GONE
            } else {
                dragButton.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        mStartDragListener.requestDrag(this)
                    }
                    false
                }
                labelEditText.text = string
                editText.apply {
                    hint = string
                    doOnTextChanged { text, _, _, _ ->
                        clearButton.isVisible = !text.isNullOrEmpty()
                        labelEditText.animate().alpha(
                            if (!text.isNullOrEmpty()) 1f else 0f
                        ).setDuration(300).start()
                        customLabels[labelInt] = text.toString()
                    }
                    setText(customLabels[labelInt])
                    clearButton.setOnClickListener {
                        setText("")
                        labelEditText.animate().alpha(0f).setDuration(300).start()
                        clearButton.visibility = View.GONE
                    }
                }

                hideButton.setImageResource(
                    if (data.indexOf(CATEGORY_HIDDEN) < absoluteAdapterPosition)
                        R.drawable.ic_visible else R.drawable.ic_invisible
                )

                hideButton.setOnClickListener {
                    if (data.indexOf(CATEGORY_HIDDEN) > absoluteAdapterPosition) {
                        if (data.indexOf(CATEGORY_HIDDEN) == 2) {
                            Toast.makeText(
                                mContext,
                                mContext.getString(R.string.cant_hide_last_category),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onRowMoved(absoluteAdapterPosition, 6)
                            hideButton.setImageResource(R.drawable.ic_visible)
                        }
                    } else {
                        onRowMoved(absoluteAdapterPosition, data.indexOf(CATEGORY_HIDDEN))
                        hideButton.setImageResource(R.drawable.ic_invisible)
                    }
                }
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val to = if (toPosition == 0 || (data.indexOf(CATEGORY_HIDDEN)==2 && fromPosition < toPosition))
            1 else toPosition
        if (fromPosition < to) {
            for (i in fromPosition until to) {
                Collections.swap(data, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo to + 1) {
                Collections.swap(data, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, to)
    }

    @SuppressLint("ResourceType")
    override fun onRowSelected(myViewHolder: MyViewHolder?) {
        val tp = mContext.obtainStyledAttributes(intArrayOf(R.attr.multiChoiceSelectorColor))
        myViewHolder!!.rowView.setBackgroundColor(tp.getColor(0, 0))
        tp.recycle()
    }

    @SuppressLint("ResourceType")
    override fun onRowClear(myViewHolder: MyViewHolder?) {
        myViewHolder!!.hideButton.setImageResource(
            if (data.indexOf(CATEGORY_HIDDEN) < myViewHolder.absoluteAdapterPosition)
                R.drawable.ic_visible else R.drawable.ic_invisible
        )

        val tp = mContext.obtainStyledAttributes(intArrayOf(
            R.attr.multiChoiceSelectorColor,
            R.attr.backgroundColor
        ))
        ValueAnimator.ofObject(
            ArgbEvaluator(),
            tp.getColor(0, 0),
            tp.getColor(1, 0)
        ).apply {
            duration = 150
            addUpdateListener { animator ->
                myViewHolder.rowView.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
        tp.recycle()
    }

    override fun getItemCount(): Int = data.size

}