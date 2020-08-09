package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseBooleanArray
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.moveTo
import com.bruhascended.sms.ui.listViewAdapter.ConversationListViewAdaptor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class CategoryFragment : Fragment() {

    private var label: Int = 0

    companion object {
        private const val ARG_SECTION= "section_conversations"
        @JvmStatic
        fun newInstance(label: Int): CategoryFragment {
            return CategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION, label)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = arguments?.getInt(ARG_SECTION)!!
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        val listView: ListView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)
        val mContext = requireActivity()

        textView.visibility = TextView.INVISIBLE

        val intent = Intent(mContext, ConversationActivity::class.java)

        var recyclerViewState: Parcelable
        mainViewModel!!.daos!![label].loadAll().observe(viewLifecycleOwner, Observer<List<Conversation>> {
            recyclerViewState = listView.onSaveInstanceState()!!
            val editListAdapter = ConversationListViewAdaptor(mContext, it)
            listView.adapter = editListAdapter
            listView.onRestoreInstanceState(recyclerViewState)

            listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                intent.putExtra("ye", it[i])
                startActivity(intent)
            }

            if (it.isEmpty()) textView.visibility = TextView.VISIBLE
            else textView.visibility = TextView.INVISIBLE

            var rangeSelect = false
            var previousSelected = -1
            var actionMenu: Menu? = null

            mainViewModel!!.selection.observe(viewLifecycleOwner, Observer<Int> { int ->
                if (int == -1) {
                    listView.choiceMode = ListView.CHOICE_MODE_NONE
                    listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                }
            })

            listView.setMultiChoiceModeListener(object : AbsListView.MultiChoiceModeListener {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

                override fun onDestroyActionMode(mode: ActionMode) {
                    editListAdapter.removeSelection()
                    mainViewModel!!.selection.postValue(-1)
                }

                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    actionMenu = menu
                    mode.menuInflater.inflate(R.menu.conversation_selection, menu)
                    rangeSelect = false
                    previousSelected = -1
                    mainViewModel!!.selection.postValue(label)
                    return true
                }

                fun toggleRange(item: MenuItem): Boolean {
                    val inf = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val iv = inf.inflate(R.layout.view_button_transition, null) as ImageView

                    if (rangeSelect) iv.setImageResource(R.drawable.range_to_single)
                    else iv.setImageResource(R.drawable.single_to_range)
                    item.actionView = iv
                    (iv.drawable as AnimatedVectorDrawable).start()

                    rangeSelect = !rangeSelect
                    if (rangeSelect) previousSelected = -1
                    GlobalScope.launch {
                        delay(300)
                        activity?.runOnUiThread {
                            if (!rangeSelect) {
                                item.setIcon(R.drawable.ic_single)
                            } else {
                                item.setIcon(R.drawable.ic_range)
                            }
                            item.actionView = null
                        }
                    }
                    return true
                }

                @SuppressLint("InflateParams")
                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    val selected: SparseBooleanArray = editListAdapter.getSelectedIds()
                    return when (item.itemId) {
                        R.id.action_select_range -> toggleRange(item)
                        R.id.action_select_all -> {
                            if (rangeSelect) toggleRange(actionMenu!!.findItem(R.id.action_select_range))
                            for (i in 0 until editListAdapter.count) {
                                if (!listView.isItemChecked(i)) {
                                    listView.setItemChecked(i, true)
                                }
                            }
                            true
                        }
                        R.id.action_delete -> {
                            AlertDialog.Builder(mContext).setTitle("Do you want to delete selected conversations?")
                                .setPositiveButton("Delete") { dialog, _ ->
                                    for (i in 0 until selected.size()) {
                                        if (selected.valueAt(i)) {
                                            val selectedItem: Conversation = editListAdapter.getItem(selected.keyAt(i))
                                            moveTo(selectedItem, -1, mContext)
                                        }
                                    }

                                    Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .create().show()
                            true
                        }
                        R.id.action_block -> {
                            AlertDialog.Builder(mContext).setTitle("Do you want to block selected conversations?")
                                .setPositiveButton("Block") { dialog, _ ->
                                    for (i in 0 until selected.size())
                                        if (selected.valueAt(i))
                                            moveTo(editListAdapter.getItem(selected.keyAt(i)), 5)
                                    Toast.makeText(mContext,"Senders Blocked", Toast.LENGTH_LONG).show()
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .create().show()
                            true
                        }
                        R.id.action_report_spam -> {
                            AlertDialog.Builder(mContext).setTitle("Do you want to report selected conversations?")
                                .setPositiveButton("Report") { dialog, _ ->
                                    for (i in 0 until selected.size())
                                        if (selected.valueAt(i))
                                            moveTo(editListAdapter.getItem(selected.keyAt(i)), 4)
                                    Toast.makeText(mContext, "Senders Reported Spam", Toast.LENGTH_LONG).show()
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .create().show()
                            true
                        }
                        R.id.action_move -> {
                            val choices = Array(4){ its -> mContext.resources.getString(labelText[its])}
                            var selection = label
                            AlertDialog.Builder(mContext).setTitle("Move this conversation to")
                                .setSingleChoiceItems(choices, selection) { _, select -> selection = select}
                                .setPositiveButton("Move") { dialog, _ ->
                                    for (i in 0 until selected.size())
                                        if (selected.valueAt(i))
                                            moveTo(editListAdapter.getItem(selected.keyAt(i)), selection)
                                    Toast.makeText(mContext, "Conversations Moved", Toast.LENGTH_LONG).show()
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    mode.finish()
                                    dialog.dismiss()
                                }
                                .create().show()
                            true
                        }
                        else -> false
                    }
                }

                override fun onItemCheckedStateChanged(
                    mode: ActionMode, position: Int, id: Long, checked: Boolean
                ) {
                    if (rangeSelect) {
                        previousSelected = if (previousSelected == -1) {
                            position
                        } else {
                            val low = Integer.min(previousSelected, position) + 1
                            val high = Integer.max(previousSelected, position) - 1
                            for (i in low..high) {
                                listView.setItemChecked(i, !listView.isItemChecked(i))
                                editListAdapter.toggleSelection(i)
                            }
                            -1
                        }
                    }
                    editListAdapter.toggleSelection(position)
                    mode.title = "${listView.checkedItemCount} selected"
                }
            })
        })

        return root
    }
}