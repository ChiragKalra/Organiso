package com.bruhascended.organiso

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.constants.EXTRA_SENDER
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.core.db.MessageDatabase
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.db.ScheduledMessage
import com.bruhascended.organiso.common.getSharable
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import com.bruhascended.organiso.services.ScheduledManager
import com.bruhascended.organiso.ui.scheduled.ScheduledRecyclerAdapter
import com.bruhascended.organiso.ui.scheduled.ScheduledViewHolder
import kotlinx.android.synthetic.main.activity_scheduled.*
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

class ScheduledActivity : AppCompatActivity() {

    private val actionCancel = 0
    private val actionCopy = 1
    private val actionShare = 2

    private val mContext = this
    private val mList = ArrayList<ScheduledMessage>()

    private lateinit var mActionArray: Array<String>
    private lateinit var mClipboardManager: ClipboardManager
    private lateinit var mAdapter: ScheduledRecyclerAdapter

    private fun actionSelected(holder: ScheduledViewHolder, action: Int) {
        when(action) {
            actionCancel -> {
                MessageDbFactory(mContext).of(holder.message.cleanAddress).apply {
                    ScheduledManager(mContext, manager()).remove(holder.message)
                    close()
                    val ind = mList.indexOf(holder.message)
                    mList.removeAt(ind)
                    mAdapter.notifyItemRemoved(ind)
                    emptyList.isVisible = mList.isEmpty()
                }
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
            actionShare -> {
                mContext.startActivity(
                    Intent.createChooser(
                        File(holder.message.path!!).getSharable(mContext),
                        mContext.getString(R.string.share)
                    )
                )
            }
        }
    }

    private fun setupSavedRecycler() {
        emptyList.isVisible = true
        recyclerView.apply {
            layoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            mAdapter = ScheduledRecyclerAdapter(mContext, mList)
            adapter = mAdapter

            mAdapter.setOnItemClickListener {
                AlertDialog.Builder(mContext).setItems(
                    mActionArray.filter { action ->
                        !(action == mActionArray[actionShare] &&  it.message.path == null)
                    }.toTypedArray()
                ) { d, c ->
                    actionSelected(it, c)
                    d.dismiss()
                }.create().show()
            }
        }
    }

    private fun addToRecycler(db: MessageDatabase) {
        val got = db.manager().loadScheduledSync()
        db.close()
        if (got.isNotEmpty()) {
            recyclerView.post {
                emptyList.isVisible = false
                val pos = mList.size
                mList.addAll(got)
                mAdapter.notifyItemRangeInserted(pos, got.size)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_scheduled)
        setupToolbar(toolbar)

        mActionArray = resources.getStringArray(R.array.scheduled_actions)
        mClipboardManager = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val sender = intent.getStringExtra(EXTRA_SENDER)
        setupSavedRecycler()
        Thread {
            val daos = MainDaoProvider(mContext).getMainDaos()
            if (sender == null) {
                for (dao in daos) {
                    for (con in dao.loadAllSync()) {
                        addToRecycler(MessageDbFactory(mContext).of(con.clean))
                    }
                }
            } else {
                addToRecycler(MessageDbFactory(mContext).of(sender))
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressed()
            false
        } else {
            super.onOptionsItemSelected(item)
        }
    }
}