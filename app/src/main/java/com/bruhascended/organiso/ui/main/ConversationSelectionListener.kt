package com.bruhascended.organiso.ui.main

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.bruhascended.organiso.R
import com.bruhascended.core.analytics.AnalyticsLogger
import com.bruhascended.core.db.Conversation
import com.bruhascended.organiso.common.ListSelectionManager
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.organiso.common.requestSpamReportPref
import com.bruhascended.organiso.notifications.NotificationActionReceiver.Companion.cancelNotification
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
    private val label: Int,
    private val actionModeCallBack: SimpleActionModeCallBack,
): ListSelectionManager.SelectionCallBack<Conversation> {

    interface SimpleActionModeCallBack {
        fun onCreateActionMode()
        fun onDestroyActionMode()
    }

    private var actionMenu: Menu? = null
    private lateinit var muteItem: MenuItem
    private var unMuteItem = false
    private val analyticsLogger = AnalyticsLogger(mContext)
    private val notificationManager = NotificationManagerCompat.from(mContext)

    lateinit var selectionManager: ListSelectionManager<Conversation>

    @SuppressLint("InflateParams")
    private fun toggleRange(item: MenuItem): Boolean {
        if (selectionManager.isRangeMode && selectionManager.isRangeSelected) return true
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
        actionModeCallBack.onCreateActionMode()
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
            .setNegativeButton(mContext.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }

        when (item.itemId) {
            R.id.action_select_range -> toggleRange(item)
            R.id.action_delete -> {
                alertDialog.setTitle(mContext.getString(R.string.delete_conversations_query))
                    .setPositiveButton(mContext.getString(R.string.delete)) { dialog, _ ->
                        for (selectedItem in selected) {
                            mContext.cancelNotification(selectedItem.number, selectedItem.id)
                            analyticsLogger.log("${selectedItem.label}_to_-1")
                            selectedItem.moveTo(-1, mContext)
                        }
                        Toast.makeText(mContext, mContext.getString(R.string.deleted), Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_block -> {
                alertDialog.setTitle(mContext.getString(R.string.block_conversations_query))
                    .setPositiveButton(mContext.getString(R.string.block)) { dialog, _ ->
                        for (selectedItem in selected) {
                            analyticsLogger.log("${selectedItem.label}_to_5")
                            mContext.cancelNotification(selectedItem.number, selectedItem.id)
                            selectedItem.moveTo(5, mContext)
                        }
                        Toast.makeText(mContext, mContext.getString(R.string.senders_blocked), Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_report_spam -> {
                alertDialog.setTitle(mContext.getString(R.string.report_conversations_query))
                    .setPositiveButton(mContext.getString(R.string.report)) { dialog, _ ->
                        for (selectedItem in selected) {
                            analyticsLogger.reportSpam(selectedItem)
                            analyticsLogger.log("${selectedItem.label}_to_4")
                            mContext.cancelNotification(selectedItem.number, selectedItem.id)
                            selectedItem.moveTo(4, mContext)
                        }
                        Toast.makeText(mContext, mContext.getString(R.string.senders_reported_spam), Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
                (mContext as AppCompatActivity).requestSpamReportPref()
            }
            R.id.action_move -> {
                val choices = ArrayList<String>().apply {
                    val labelArr = mContext.resources.getStringArray(R.array.labels)
                    for (i in 0..3) {
                        if (i!=label) {
                            add(labelArr[i])
                        }
                    }
                }.toTypedArray()
                var selection = 0
                alertDialog.setTitle(mContext.getString(R.string.move_conversations_to))
                    .setSingleChoiceItems(choices, selection) { _, select ->
                        selection = select + if (select>=label) 1 else 0
                    }.setPositiveButton(mContext.getString(R.string.move)) { dialog, _ ->
                        for (selectedItem in selected) {
                            analyticsLogger.log("${selectedItem.label}_to_$selection")
                            selectedItem.moveTo(selection, mContext)
                        }
                        Toast.makeText(mContext, mContext.getString(R.string.conversations_moved), Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.create().show()
            }
            R.id.action_mute -> {
                selected.forEach {
                    it.apply {
                        isMuted = !isMuted
                        if (isMuted) mContext.cancelNotification(number, id)
                        MainDaoProvider(mContext).getMainDaos()[label].insert(this)
                    }
                }
                selectionManager.close()
            }
            android.R.id.home -> selectionManager.close()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        actionModeCallBack.onDestroyActionMode()
        selectionManager.close()
    }
}