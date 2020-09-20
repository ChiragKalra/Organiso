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

package com.bruhascended.sms.ui.main

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.ui.mainViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class CategoryFragment(
    private var pos: Int = 0,
    private var label: Int = 0
) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val mContext = requireActivity()
        val dark = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("dark_theme", false)
        inflater.cloneInContext(ContextThemeWrapper(mContext, if (dark) R.style.DarkTheme else R.style.LightTheme))
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        val listView: ListView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)
        val progressView: ProgressBar = root.findViewById(R.id.loading)

        textView.visibility = TextView.INVISIBLE

        val intent = Intent(mContext, ConversationActivity::class.java)

        GlobalScope.launch {
            delay(if (pos == 0) 0 else 500)
            (mContext as Activity).runOnUiThread {
                mainViewModel.daos[label].loadAll().observe(viewLifecycleOwner, {
                    progressView.visibility = View.GONE
                    if (it.isEmpty()) textView.visibility = TextView.VISIBLE
                    else textView.visibility = TextView.INVISIBLE

                    listView.apply {
                        val listViewState = onSaveInstanceState()!!
                        adapter = ConversationListViewAdaptor(mContext, it.toMutableList())
                        onRestoreInstanceState(listViewState)
                        onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                            intent.putExtra("ye", it[i])
                            startActivity(intent)
                        }
                        setMultiChoiceModeListener(
                            ConversationMultiChoiceModeListener(mContext, listView, label)
                        )
                        mainViewModel.selection.observe(viewLifecycleOwner, { int ->
                            if (int == -1) {
                                choiceMode = ListView.CHOICE_MODE_NONE
                                choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                            }
                        })
                    }
                })
            }
        }
        return root
    }
}