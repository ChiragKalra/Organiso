package com.bruhascended.sms.ui.main

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.bruhascended.sms.R
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ui.common.ListSelectionManager
import com.bruhascended.sms.ui.common.ListSelectionManager.Companion.SelectionRecyclerAdaptor
import com.bruhascended.sms.ui.common.ScrollEffectFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

@Suppress("UNCHECKED_CAST")
class CategoryFragment: Fragment() {

    private val labelArg = "LABEL"
    private val posArg = "POSITION"

    private lateinit var selectionManager: ListSelectionManager<Conversation>
    private lateinit var mAdaptor: ConversationRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var mListener: ConversationSelectionListener
    private lateinit var recyclerView: RecyclerView

    private val dataObserver = object: RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
                super.onItemRangeInserted(positionStart, itemCount)
                recyclerView.scrollToPosition(0)
            } else {
                super.onItemRangeInserted(positionStart, itemCount)
            }
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            if (mLayoutManager.findFirstVisibleItemPosition() == 0) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                recyclerView.scrollToPosition(0)
            } else {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
            }
        }
    }

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
        recyclerView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)

        textView.visibility = TextView.INVISIBLE

        if (::selectionManager.isInitialized) selectionManager.close()

        val flow = Pager(PagingConfig(
            pageSize = 1,
            initialLoadSize = 1,
            prefetchDistance = 30,
            maxSize = PagingConfig.MAX_SIZE_UNBOUNDED,
        )) {
            mainViewModel.daos[label].loadAllPaged()
        }.flow.cachedIn(mContext.lifecycleScope)

        if (mainViewModel.daos[label].loadSingle()==null) textView.visibility = TextView.VISIBLE
        else textView.visibility = TextView.INVISIBLE

        mAdaptor = ConversationRecyclerAdaptor(mContext)
        mListener =  ConversationSelectionListener(mContext, label)
        selectionManager = ListSelectionManager(
            mContext as AppCompatActivity,
            mAdaptor as SelectionRecyclerAdaptor<Conversation, RecyclerView.ViewHolder>,
            mListener
        )
        mAdaptor.selectionManager = selectionManager
        mAdaptor.registerAdapterDataObserver(dataObserver)
        mListener.selectionManager = selectionManager
        mLayoutManager = LinearLayoutManager(mContext).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
        recyclerView.apply {
            layoutManager = mLayoutManager
            isNestedScrollingEnabled = true
            adapter = mAdaptor
            edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
        }

        mContext.lifecycleScope.launch {
            flow.collectLatest {
                if (mainViewModel.daos[label].loadSingle()==null) textView.visibility = TextView.VISIBLE
                else textView.visibility = TextView.INVISIBLE
                mAdaptor.submitData(it)
            }
        }
        return root
    }
}