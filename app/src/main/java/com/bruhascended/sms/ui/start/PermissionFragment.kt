package com.bruhascended.sms.ui.start

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bruhascended.sms.R


class PermissionFragment (context: Context) : Fragment() {

    private val mContext = context
    private lateinit var pageViewModel: StartViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(StartViewModel::class.java).apply {
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_main, container, false)
        val listView: ListView = root.findViewById(R.id.listView)
        return root
    }

    companion object {
        private const val ARG_SECTION= "section_conversations"
        @JvmStatic
        fun newInstance(context: Context, conversations: String): PermissionFragment {
            return PermissionFragment(context).apply {
                arguments = Bundle().apply {
                    putString(ARG_SECTION, conversations)
                }
            }
        }
    }
}