package com.bruhascended.sms.ui.conversastion

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.core.content.FileProvider
import com.bruhascended.sms.NewConversationActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.activeConversationDao
import com.bruhascended.sms.db.Conversation
import com.bruhascended.sms.db.Message
import com.bruhascended.sms.ui.ListSelectionManager
import com.bruhascended.sms.ui.MediaPreviewManager.Companion.getMimeType
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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


@SuppressLint("InflateParams")
class MessageSelectionListener(
    private val mContext: Context,
    private val conversation: Conversation
): ListSelectionManager.SelectionCallBack<Message> {

    private lateinit var shareMenuItem: MenuItem
    private lateinit var forwardMenuItem: MenuItem

    lateinit var selectionManager: ListSelectionManager<Message>

    private fun getSharable(message: Message) : Intent {
        val mmsTypeString = getMimeType(message.path!!)
        val contentUri = FileProvider.getUriForFile(
            mContext,
            "com.bruhascended.sms.fileProvider", File(message.path!!)
        )
        mContext.grantUriPermission(
            "com.bruhascended.sms", contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        return Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = mmsTypeString
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

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
        shareMenuItem = menu.findItem(R.id.action_share)
        forwardMenuItem =  menu.findItem(R.id.action_forward)
        return true
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.message_selection, menu)
        return true
    }

    override fun onSingleItemSelected(item: Message) {
        shareMenuItem.isVisible = item.path != null
        forwardMenuItem.isVisible = item.path == null
    }

    override fun onMultiItemSelected(list: List<Message>) {
        shareMenuItem.isVisible = false
        forwardMenuItem.isVisible = false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val selected = selectionManager.selectedItems

        when (item.itemId) {
            R.id.action_delete -> {
                AlertDialog.Builder(mContext)
                    .setTitle("Delete selected messages?")
                    .setPositiveButton("Delete") { dialog, _ ->
                        Thread {
                            for (selectedItem in selected)
                                activeConversationDao.delete(selectedItem)
                        }.start()
                        Toast.makeText(mContext, "Deleted", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                        selectionManager.close()
                    }.setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }.create().show()
            }
            R.id.action_select_range -> toggleRange(item)
            R.id.action_copy -> {
                val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val sb = StringBuilder()
                for (selectedItem in selected) {
                    sb.append(selectedItem.text).append('\n')
                }
                val clip = ClipData.newPlainText("none", sb.toString())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(mContext, "Copied", Toast.LENGTH_LONG).show()
                mode.finish()
            }
            R.id.action_share -> {
                mContext.startActivity(
                    Intent.createChooser(
                        getSharable(selected.first()),
                        "Share"
                    )
                )
                mode.finish()
            }
            R.id.action_forward -> {
                val intent = Intent(mContext, NewConversationActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, selected.first().text)
                }
                mContext.startActivity(intent)
                mode.finish()
            }
            android.R.id.home -> selectionManager.close()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = selectionManager.close()
}