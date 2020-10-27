package com.bruhascended.organiso

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.common.getSharable
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import com.bruhascended.organiso.ui.saved.SavedRecyclerAdaptor
import com.bruhascended.organiso.ui.saved.SavedViewHolder
import com.bruhascended.organiso.ui.saved.SavedViewModel
import kotlinx.android.synthetic.main.activity_saved.*
import kotlinx.coroutines.flow.collectLatest
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

class SavedActivity : AppCompatActivity() {

    private val actionSend = 0
    private val actionCopy = 1
    private val actionShare = 2
    private val actionGoTo = 3
    private val actionDelete = 4

    private lateinit var actionArray: Array<String>

    private lateinit var mClipboardManager: ClipboardManager
    private val mContext = this
    private val mViewModel: SavedViewModel by viewModels()

    private fun actionSelected(holder: SavedViewHolder, action: Int) {
        when(action) {
            actionDelete -> {
                mViewModel.delete(holder.message)
                Toast.makeText(
                    mContext, mContext.getString(R.string.deleted), Toast.LENGTH_LONG
                ).show()
            }
            actionCopy -> {
                val clip = ClipData.newPlainText("none", holder.message.text)
                mClipboardManager.setPrimaryClip(clip)
                Toast.makeText(
                    mContext, mContext.getString(R.string.copied), Toast.LENGTH_LONG
                ).show()
            }
            actionSend -> {
                val intent = Intent(mContext, NewConversationActivity::class.java).apply {
                    this.action = Intent.ACTION_SEND
                    val path = holder.message.path
                    if (path == null) {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, holder.message.text)
                    } else {
                        type = getMimeType(path)
                        data = Uri.fromFile(File(path))
                    }
                }
                mContext.startActivity(intent)
            }
            actionShare -> {
                mContext.startActivity(
                    Intent.createChooser(
                        File(holder.message.path!!).getSharable(mContext),
                        mContext.getString(R.string.share)
                    )
                )
            }
            actionGoTo -> {
                if (holder.message.messageId == null) {
                    Toast.makeText(
                        mContext, getString(R.string.message_was_deleted), Toast.LENGTH_LONG
                    ).show()
                } else {
                    startActivity(
                        Intent(mContext, ConversationActivity::class.java)
                            .putExtra(EXTRA_NUMBER, holder.message.sender)
                            .putExtra(EXTRA_MESSAGE_ID, holder.message.messageId)
                    )
                }
            }
        }
    }

    private fun setupSavedRecycler() {
        recyclerView.apply {
            addItemDecoration(DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL))
            layoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            val mAdapter = SavedRecyclerAdaptor(mContext)
            adapter = mAdapter
            emptyList.isVisible = mViewModel.dbIsEmpty()
            lifecycleScope.launch {
                mViewModel.flow.cachedIn(this).collectLatest {
                    mAdapter.submitData(it)
                    emptyList.isVisible = mViewModel.dbIsEmpty()
                }
            }

            mAdapter.setOnItemClickListener {
                val mActions = ArrayList<String>().apply {
                    addAll(actionArray)
                    if (it.message.type == SAVED_TYPE_DRAFT) remove(actionArray[actionGoTo])
                    if (it.message.path == null) remove(actionArray[actionShare])
                    if (it.message.text.isBlank()) remove(actionArray[actionCopy])
                }
                AlertDialog.Builder(mContext).setItems(mActions.toTypedArray()) { d, c ->
                    actionSelected(it, actionArray.indexOf(mActions[c]))
                    d.dismiss()
                }.create().show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_saved)
        setupToolbar(toolbar)


        actionArray = resources.getStringArray(R.array.saved_actions)
        mClipboardManager = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        setupSavedRecycler()
    }
}