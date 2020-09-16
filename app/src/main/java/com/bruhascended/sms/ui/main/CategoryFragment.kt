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
        val mContext = requireActivity()
        val dark = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("dark_theme", false)
        inflater.cloneInContext(ContextThemeWrapper(mContext, if (dark) R.style.DarkTheme else R.style.LightTheme))
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        val listView: ListView = root.findViewById(R.id.listView)
        val textView: TextView = root.findViewById(R.id.emptyList)
        val progressView: ProgressBar = root.findViewById(R.id.loading)

        textView.visibility = TextView.INVISIBLE

        val intent = Intent(mContext, ConversationActivity::class.java)

        val promo = PreferenceManager.getDefaultSharedPreferences(mContext)
            .getBoolean("promotions_category_visible", true)
        GlobalScope.launch {
            delay(
                when(label) {
                    1 -> 500
                    2 -> 500
                    3 -> if (promo) 700 else 0
                    else -> 0
                }
            )
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