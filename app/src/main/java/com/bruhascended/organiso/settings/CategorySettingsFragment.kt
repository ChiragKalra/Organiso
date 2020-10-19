package com.bruhascended.organiso.settings

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
import com.bruhascended.organiso.R
import com.bruhascended.organiso.settings.GeneralFragment.Companion.KEY_STATE_CHANGED
import com.bruhascended.organiso.settings.GeneralFragment.Companion.PREF_DARK_THEME
import com.bruhascended.organiso.settings.categories.ItemMoveCallback
import com.bruhascended.organiso.settings.categories.RecyclerViewAdapter
import com.bruhascended.organiso.settings.categories.RecyclerViewAdapter.Companion.CATEGORY_HIDDEN
import com.bruhascended.organiso.settings.categories.RecyclerViewAdapter.Companion.CATEGORY_VISIBLE
import com.google.gson.Gson
import kotlin.collections.ArrayList

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
class CategorySettingsFragment: Fragment(), RecyclerViewAdapter.StartDragListener {

    companion object {
        const val PREF_VISIBLE_CATEGORIES = "visible_categories"
        const val PREF_HIDDEN_CATEGORIES = "hidden_categories"

        val ARR_PREF_CUSTOM_LABELS = Array(6) {
            "custom_label_${it}"
        }

        fun String?.toLabelArray(): Array<Int> = Gson().fromJson(this,  Array<Int>::class.java)

        fun Array<Int>.toJson(): String = Gson().toJson(this)

    }

    private lateinit var touchHelper: ItemTouchHelper
    private lateinit var prefs: SharedPreferences
    private lateinit var mAdapter: RecyclerViewAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var previousOrder: Array<Int>
    private lateinit var previousLabels: Array<String>

    private fun drawRecyclerView(
        visibleCategories: Array<Int>,
        hiddenCategories: Array<Int>,
        labels: Array<String>
    ) {
        val currentOrder = ArrayList<Int>().apply {
            add(CATEGORY_VISIBLE)
            for (category in visibleCategories) add(category)
            add(CATEGORY_HIDDEN)
            for (category in hiddenCategories) add(category)
        }

        previousOrder = currentOrder.toTypedArray()

        mAdapter = RecyclerViewAdapter(requireContext(), currentOrder, this, labels)

        val callback: ItemTouchHelper.Callback = ItemMoveCallback(mAdapter)
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recycler)

        recycler.adapter = mAdapter
    }

    private fun getLabels() = Array(6) {
        prefs.getString(ARR_PREF_CUSTOM_LABELS[it], "")!!
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        val dark = prefs.getBoolean(PREF_DARK_THEME, false)
        inflater.cloneInContext(
            ContextThemeWrapper(
                requireActivity(),
                if (dark) R.style.DarkTheme else R.style.LightTheme
            )
        )

        val root = inflater.inflate(R.layout.fragment_settings_category, container, false)
        recycler = root.findViewById(R.id.recycler)

        val visibleCategories =
            prefs.getString(PREF_VISIBLE_CATEGORIES, "").toLabelArray()
        val hiddenCategories =
            prefs.getString(PREF_HIDDEN_CATEGORIES, "").toLabelArray()

        recycler.layoutManager = LinearLayoutManager(requireContext()).apply {
            orientation = LinearLayoutManager.VERTICAL
        }

        previousLabels = getLabels()
        drawRecyclerView(visibleCategories, hiddenCategories, previousLabels)

        return root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.category_settings, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.reset_to_default_query))
                    .setPositiveButton(getString(R.string.reset)) { dialog, _ ->
                        val vis = Array(4){it}
                        val hid = Array(2){4+it}
                        prefs.edit()
                            .putString(PREF_VISIBLE_CATEGORIES, vis.toJson())
                            .putString(PREF_HIDDEN_CATEGORIES, hid.toJson())
                            .apply {
                                for (i in 0..5) remove(ARR_PREF_CUSTOM_LABELS[i])
                            }.apply()
                        drawRecyclerView(vis, hid, Array(6){""})
                        dialog.dismiss()
                    }
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }.create().show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        val arr = mAdapter.data
        if (
            arr.toTypedArray() contentDeepEquals previousOrder &&
            previousLabels contentDeepEquals mAdapter.customLabels
        ) {
            super.onDestroy()
            return
        }

        val hidePos = arr.indexOf(CATEGORY_HIDDEN)
        val vis = Array(hidePos-1){arr[it+1]}
        val hid = Array(7-hidePos){arr[it+hidePos+1]}
        prefs.edit()
            .putBoolean(KEY_STATE_CHANGED, true)
            .putString(PREF_VISIBLE_CATEGORIES, vis.toJson())
            .putString(PREF_HIDDEN_CATEGORIES, hid.toJson())
            .apply()

        super.onDestroy()
    }

    override fun requestDrag(viewHolder: ViewHolder) {
        touchHelper.startDrag(viewHolder)
    }
}