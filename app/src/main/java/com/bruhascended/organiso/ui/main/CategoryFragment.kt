package com.bruhascended.organiso.ui.main

import android.graphics.Rect
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.core.db.Conversation
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.ListSelectionManager
import com.bruhascended.organiso.common.ListSelectionManager.SelectionRecyclerAdaptor
import com.bruhascended.organiso.common.ScrollEffectFactory
import com.bruhascended.core.constants.*
import kotlinx.coroutines.delay
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
class CategoryFragment: Fragment(), ConversationSelectionListener.SimpleActionModeCallBack {

    companion object {
        fun newInstance(label: Int) : CategoryFragment {
            return CategoryFragment().apply {
                arguments = Bundle().apply {
                    putInt(labelArg, label)
                }
            }
        }
    }

    private val labelArg = "LABEL"

    private lateinit var selectionManager: ListSelectionManager<Conversation>
    private lateinit var mAdaptor: ConversationRecyclerAdaptor
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var mListener: ConversationSelectionListener
    private lateinit var recyclerView: RecyclerView
    private var swipeHelper: ItemTouchHelper? = null

    private var label: Int = 0
    private val model: MainViewModel by activityViewModels()

    private val dataObserver = object: RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            super.onItemRangeInserted(positionStart, itemCount)
            if (mLayoutManager.findFirstVisibleItemPosition() == 0 && positionStart==0) {
                recyclerView.scrollToPosition(0)
            }
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            super.onItemRangeMoved(fromPosition, toPosition, itemCount)
            if (mLayoutManager.findFirstVisibleItemPosition() == 0 && toPosition==0) {
                recyclerView.scrollToPosition(0)
            }
        }
    }

    class FooterDecoration(private val footerHeight: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val adapter = parent.adapter ?: return
            when (parent.getChildAdapterPosition(view)) {
                adapter.itemCount - 1 ->
                    outRect.bottom = footerHeight
                else ->
                    outRect.set(0, 0, 0, 0)
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
        val dark = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(
            PREF_DARK_THEME,
            false
        )
        inflater.cloneInContext(
            ContextThemeWrapper(
                mContext,
                if (dark) R.style.DarkTheme else R.style.LightTheme
            )
        )
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        recyclerView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)

        textView.visibility = TextView.INVISIBLE

        if (::selectionManager.isInitialized) selectionManager.close()

        val flow =
            model.categoryFlows[label].cachedIn(mContext.lifecycleScope)

        mAdaptor = ConversationRecyclerAdaptor(mContext)
        mListener =  ConversationSelectionListener(mContext, label, this)
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
            val height = mContext.resources.displayMetrics.density * 84
            addItemDecoration(FooterDecoration(height.toInt()))
            edgeEffectFactory = ScrollEffectFactory()
            addOnScrollListener(ScrollEffectFactory.OnScrollListener())
        }


        mContext.lifecycleScope.launch {
            delay(if (model.isDelayed(label)) 500 else 0)
            flow.collectLatest {
                textView.isVisible = model.isEmpty(label)
                mAdaptor.submitData(it)
            }
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        if (model.prefs.getBoolean(PREF_ACTION_CUSTOM, false)) {
            val cancelCallBack: (RecyclerView.ViewHolder?) -> Unit = {
                if (it != null) {
                    mAdaptor.notifyItemChanged(it.absoluteAdapterPosition)
                }
            }

            val callback = SwipeActionCallback(
                requireContext(),
                cancelCallBack,
                model.prefs.getString(PREF_CUSTOM_LEFT, ACTION_MOVE)!!,
                model.prefs.getString(PREF_CUSTOM_RIGHT, ACTION_MOVE)!!,
                model.prefs.getInt(PREF_CUSTOM_STRENGTH, 3),
            )
            swipeHelper?.attachToRecyclerView(null)
            swipeHelper = ItemTouchHelper(callback)
            swipeHelper?.attachToRecyclerView(recyclerView)
        } else {
            swipeHelper?.attachToRecyclerView(null)
        }
    }

    override fun onCreateActionMode() {
        swipeHelper?.attachToRecyclerView(null)
    }

    override fun onDestroyActionMode() {
        swipeHelper?.attachToRecyclerView(recyclerView)
    }
}