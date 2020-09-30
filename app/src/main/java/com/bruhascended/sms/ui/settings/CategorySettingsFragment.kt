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

package com.bruhascended.sms.ui.settings

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.bruhascended.sms.R
import com.google.gson.Gson
import kotlin.collections.ArrayList

@Suppress("UNCHECKED_CAST")
class CategorySettingsFragment: Fragment(), RecyclerViewAdapter.StartDragListener {

    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var mAdapter: RecyclerViewAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var previousOrder: Array<Int>
    private val gson = Gson()

    private fun drawRecyclerView(
        visibleCategories: Array<Int>,
        hiddenCategories: Array<Int>
    ) {
        val currentOrder = ArrayList<Int>().apply {
            add(VISIBLE)
            for (category in visibleCategories) add(category)
            add(HIDDEN)
            for (category in hiddenCategories) add(category)
        }

        previousOrder = currentOrder.toTypedArray()

        mAdapter = RecyclerViewAdapter(this.requireContext(), currentOrder, this, prefs)

        val callback: ItemTouchHelper.Callback = ItemMoveCallback(mAdapter)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recycler)

        recycler.adapter = mAdapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        val dark = prefs.getBoolean("dark_theme", false)
        inflater.cloneInContext(
            ContextThemeWrapper(
                requireActivity(),
                if (dark) R.style.DarkTheme else R.style.LightTheme
            )
        )

        val root = inflater.inflate(R.layout.fragment_settings_category, container, false)
        recycler = root.findViewById(R.id.recycler)

        val visibleCategories = gson.fromJson(
            prefs.getString("visible_categories", ""), Array<Int>::class.java
        )
        val hiddenCategories = gson.fromJson(
            prefs.getString("hidden_categories", ""), Array<Int>::class.java
        )

        recycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            orientation = LinearLayoutManager.VERTICAL
        }

        drawRecyclerView(visibleCategories, hiddenCategories)

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.category_settings, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Reset to default?")
                    .setPositiveButton("Reset") { dialog, _ ->
                        val vis = Array(4){it}
                        val hid = Array(2){4+it}
                        prefs.edit()
                            .putString("visible_categories", Gson().toJson(vis))
                            .putString("hidden_categories", Gson().toJson(hid))
                            .apply {
                                for (i in 0..5) remove("custom_label_$i")
                            }.putBoolean("stateChanged", true)
                            .apply()
                        drawRecyclerView(vis, hid)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }.create().show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        val arr = mAdapter.data
        if (arr.toTypedArray() contentDeepEquals previousOrder) {
            super.onDestroy()
            return
        }
        val hidePos = arr.indexOf(HIDDEN)
        val vis = Array(hidePos-1){arr[it+1]}
        val hid = Array(7-hidePos){arr[it+hidePos+1]}
        prefs.edit()
            .putBoolean("stateChanged", true)
            .putString("visible_categories", gson.toJson(vis))
            .putString("hidden_categories", gson.toJson(hid))
            .apply()
        super.onDestroy()
    }
    override fun requestDrag(viewHolder: ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }
}