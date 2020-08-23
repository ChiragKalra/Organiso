package com.bruhascended.sms.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.mainViewModel


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
        mainViewModel.daos[label].loadAll().observe(viewLifecycleOwner, {
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

            mainViewModel.selection.observe(viewLifecycleOwner, { int ->
                if (int == -1) {
                    listView.choiceMode = ListView.CHOICE_MODE_NONE
                    listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                }
            })

            listView.setMultiChoiceModeListener(
                ConversationMultiChoiceModeListener(mContext, listView, label)
            )
        })

        return root
    }
}