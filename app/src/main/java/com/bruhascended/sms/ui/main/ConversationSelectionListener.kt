package com.bruhascended.sms.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import android.widget.ImageView
import android.widget.Toast
import com.bruhascended.sms.R
import com.bruhascended.sms.analytics.AnalyticsLogger
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.moveTo
import com.bruhascended.sms.mainViewModel
import com.bruhascended.sms.ui.ListSelectionManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
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

class ConversationSelectionListener(
    private val mContext: Context,
    private val label: Int
): ListSelectionManager.SelectionCallBack<Conversation> {

    private var actionMenu: Menu? = null
    private lateinit var muteItem: MenuItem
    private var unMuteItem = false
    private val analyticsLogger = AnalyticsLogger(mContext)

    lateinit var selectionManager: ListSelectionManager<Conversation>

    @SuppressLint("InflateParams")
    private fun toggleRange(item: MenuItem): Boolean {
        val inf = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val iv = inf.inflate(R.layout.view_button_transition, null) as ImageView

        iv.setImageResource(if (selectionManager.isRangeMode)
            R.drawable.range_to_single else R.drawable.single_to_range
        )
        item.actionView = iv
        (iv.drawable as AnimatedVectorDrawable).start()

        selectionManager.toggleRangeMode()
        GlobalScope.launch {
            delay(350)
            (mContext as Activity).runOnUiThread {
                item.setIcon(if (selectionManager.isRangeMode)
                    R.drawable.ic_range else R.drawable.ic_single
                )
                item.actionView = null
            }
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        actionMenu = menu
        muteItem = menu.findItem(R.id.action_mute)
        muteItem.setIcon(if (unMuteItem) R.drawable.ic_unmute else R.drawable.ic_mute)

        if (label == 4) menu.findItem(R.id.action_report_spam).isVisible = false
        if (label == 5) menu.findItem(R.id.action_block).isVisible = false
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.conversation_selection, menu)
        return true
    }

    override fun onSingleItemSelected(item: Conversation) {
        unMuteItem = item.isMuted
        muteItem.setIcon(if (item.isMuted) R.drawable.ic_unmute else R.drawable.ic_mute)
    }

    override fun onMultiItemSelected(list: List<Conversation>) {
        unMuteItem = false
        muteItem.setIcon(R.drawable.ic_mute)
    }

    @SuppressLint("InflateParams")
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selected = selectionManager.selectedItems

        val alertDialog = AlertDialog.Builder(mContext)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                selectionManager.close()
            }

        when (item.itemId) {
            R.id.action_select_range -> toggleRange(item)
            R.id.action_select_all -> {
                if (selectionManager.isRangeMode)
                    toggleRange(actionMenu!!.findItem(R.id.action_select_range))
                selectionManager.selectAll()
            }
            R.id.action_delete -> {
                alertDialog.setTitle("Delete selected conversations?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        for (selectedItem in selected) {
                            moveTo(selectedItem, -1, mContext)
                            analyticsLogger.log("${selectedItem.label}_to_-1")
                        }
                        Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_block -> {
                alertDialog.setTitle("Block selected conversations?")
                    .setPositiveButton("Block") { dialog, _ ->
                        for (selectedItem in selected) {
                            moveTo(selectedItem, 5)
                            analyticsLogger.log("${selectedItem.label}_to_5")
                        }
                        Toast.makeText(mContext,"Senders Blocked", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_report_spam -> {
                alertDialog.setTitle("Report selected conversations?")
                    .setPositiveButton("Report") { dialog, _ ->
                        for (selectedItem in selected) {
                            moveTo(selectedItem, 4)
                            analyticsLogger.reportSpam(selectedItem)
                            analyticsLogger.log("${selectedItem.label}_to_4")
                        }
                        Toast.makeText(mContext, "Senders Reported Spam", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_move -> {
                val choices = ArrayList<String>().apply {
                    for (i in 0..3) {
                        if (i!=label) add(mContext.resources.getString(labelText[i]))
                    }
                }.toTypedArray()
                var selection = 0
                alertDialog.setTitle("Move this conversation to")
                    .setSingleChoiceItems(choices, selection) { _, select ->
                        selection = select + if (select>=label) 1 else 0
                    }.setPositiveButton("Move") { dialog, _ ->
                        for (selectedItem in selected) {
                            moveTo(selectedItem, selection)
                            analyticsLogger.log("${selectedItem.label}_to_$selection")
                        }
                        Toast.makeText(mContext, "Conversations Moved", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_mute -> {
                if (unMuteItem) {
                    selected.first().apply {
                        isMuted = !isMuted
                        mainViewModel.daos[label].update(this)
                    }
                } else {
                    for (selectedItem in selected) {
                        selectedItem.isMuted = true
                        mainViewModel.daos[selectedItem.label].update(selectedItem)
                    }
                }
                selectionManager.close()
            }
            android.R.id.home -> selectionManager.close()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = selectionManager.close()
}