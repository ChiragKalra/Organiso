package com.bruhascended.sms.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.listViewAdapter.ConversationListViewAdaptor


class CategoryFragment (context: Context, viewModel: MainViewModel) : Fragment() {

    private val mContext = context
    private var label: Int = 0
    private var mainViewModel: MainViewModel = viewModel

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

        textView.visibility = TextView.INVISIBLE

        val intent = Intent(mContext, ConversationActivity::class.java)

        Handler().postDelayed({
            mainViewModel.daos[label].loadAll()
                .observe(viewLifecycleOwner, Observer<List<Conversation>> {
                    listView.adapter = ConversationListViewAdaptor(mContext, it)

                    listView.onItemClickListener =
                        AdapterView.OnItemClickListener { _: AdapterView<*>, _: View, i: Int, _: Long ->
                            intent.putExtra("ye", it[i])
                            startActivity(intent)
                        }

                    if (it.isEmpty()) textView.visibility = TextView.VISIBLE
                    else textView.visibility = TextView.INVISIBLE
                })
        }, if (label == 0) 0 else 700L)

        return root
    }

    companion object {
        private const val ARG_SECTION= "section_conversations"
        @JvmStatic
        fun newInstance(context: Context, viewModel: MainViewModel, label: Int): CategoryFragment {
            return CategoryFragment(context, viewModel).apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION, label)
                }
            }
        }
    }
}