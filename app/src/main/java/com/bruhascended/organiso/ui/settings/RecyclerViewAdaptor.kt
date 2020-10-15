package com.bruhascended.organiso.ui.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.organiso.R
import com.bruhascended.organiso.data.SMSManager.Companion.labelText
import java.util.*

const val VISIBLE = 100
const val HIDDEN = 101


class RecyclerViewAdapter(
    private val mContext: Context,
    val data: ArrayList<Int>,
    private val mStartDragListener: StartDragListener,
    private val prefs: SharedPreferences
):
    RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder>(),
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
        val category = data[position] == HIDDEN || data[position] == VISIBLE
        val string = mContext.getString( when {
            data[position] == HIDDEN -> R.string.hidden_category
            data[position] == VISIBLE -> R.string.visible_category
            else -> labelText[data[position]]
        })

        holder.labelInt = data[position]

        if (category) {
            holder.label.text = string
            holder.label.visibility = View.VISIBLE
            holder.dragButton.visibility = View.GONE
            holder.editText.visibility = View.GONE
            holder.hideButton.visibility = View.GONE
        } else {
            holder.dragButton.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    mStartDragListener.requestDrag(holder)
                }
                false
            }
            holder.labelEditText.text = string
            holder.editText.apply {
                hint = string
                setOnFocusChangeListener { _, _ ->
                    prefs.edit().putString(
                        "custom_label_${holder.labelInt}",
                        text.toString()
                    ).putBoolean("stateChanged", true).apply()
                }
                doOnTextChanged { text, _, _, _ ->
                    if (!text.isNullOrEmpty()) {
                        holder.labelEditText.animate().alpha(1f).setDuration(300).start()
                        holder.clearButton.visibility = View.VISIBLE
                    } else {
                        holder.labelEditText.animate().alpha(0f).setDuration(300).start()
                        holder.clearButton.visibility = View.GONE
                    }

                }
                setText(prefs.getString("custom_label_${holder.labelInt}", ""))
                holder.clearButton.setOnClickListener {
                    setText("")
                    holder.labelEditText.animate().alpha(0f).setDuration(300).start()
                    holder.clearButton.visibility = View.GONE
                    prefs.edit().putString(
                        "custom_label_${holder.labelInt}", ""
                    ).putBoolean("stateChanged", true).apply()
                }
            }

            holder.hideButton.setImageResource(
                if (data.indexOf(HIDDEN) < holder.absoluteAdapterPosition)
                    R.drawable.ic_visible else R.drawable.ic_invisible
            )

            holder.hideButton.setOnClickListener {
                if (data.indexOf(HIDDEN) > holder.absoluteAdapterPosition) {
                    if (data.indexOf(HIDDEN) == 2) {
                        Toast.makeText(mContext, mContext.getString(R.string.cant_hide_last_category), Toast.LENGTH_SHORT).show()
                    } else {
                        onRowMoved(holder.absoluteAdapterPosition, 7)
                        holder.hideButton.setImageResource(R.drawable.ic_visible)
                    }
                } else {
                    onRowMoved(holder.absoluteAdapterPosition, data.indexOf(HIDDEN))
                    holder.hideButton.setImageResource(R.drawable.ic_invisible)
                }
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val to = if (toPosition == 0 || (data.indexOf(HIDDEN)==2 && fromPosition < toPosition))
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
            if (data.indexOf(HIDDEN) < myViewHolder.absoluteAdapterPosition)
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