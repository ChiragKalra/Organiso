package com.bruhascended.sms.ui.main

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.*
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.ui.ListSelectionManager

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

class CategoryFragment: Fragment() {

    private val labelArg = "LABEL"
    private val posArg = "POSITION"

    private lateinit var selectionManager: ListSelectionManager<Conversation>

    private var label: Int = 0

    companion object {
        fun newInstance(label: Int) : CategoryFragment {
            return CategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(labelArg, label)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            label = getInt(labelArg)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val mContext = requireActivity()
        val dark = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("dark_theme", false)
        inflater.cloneInContext(ContextThemeWrapper(mContext, if (dark) R.style.DarkTheme else R.style.LightTheme))
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        val listView: RecyclerView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)
        val progressView: ProgressBar = root.findViewById(R.id.loading)

        textView.visibility = TextView.INVISIBLE
        listView.layoutManager = LinearLayoutManager(mContext).apply {
            orientation = LinearLayoutManager.VERTICAL
        }

        mainViewModel.daos[label].loadAll().observe(viewLifecycleOwner, {
            if (::selectionManager.isInitialized) selectionManager.close()

            progressView.visibility = View.GONE
            if (it.isEmpty()) textView.visibility = TextView.VISIBLE
            else textView.visibility = TextView.INVISIBLE

            listView.apply {
                isNestedScrollingEnabled = true
                val listViewState = layoutManager!!.onSaveInstanceState()
                val mAdaptor = ConversationListViewAdaptor(mContext, it.toMutableList())
                val mListener =  ConversationSelectionListener(requireContext(), label)
                selectionManager = ListSelectionManager(
                    requireActivity() as AppCompatActivity, it, mAdaptor, mListener
                )
                mAdaptor.selectionManager = selectionManager
                mListener.selectionManager = selectionManager
                adapter = mAdaptor
                layoutManager!!.onRestoreInstanceState(listViewState)
            }
        })


        return root
    }
}