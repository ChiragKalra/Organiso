package com.bruhascended.organiso

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.Message
import com.bruhascended.core.db.Saved
import com.bruhascended.organiso.common.saveFile
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import com.bruhascended.organiso.ui.saved.SavedViewModel
import com.bruhascended.organiso.ui.saved.TagActivity
import com.bruhascended.organiso.ui.saved.TagRecyclerAdaptor
import kotlinx.android.synthetic.main.activity_saved.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


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
class SavedActivity : AppCompatActivity() {
    private val mViewModel: SavedViewModel by viewModels()
    private lateinit var mPrefs: SharedPreferences
    private val mContext = this
    private lateinit var mAdapter: TagRecyclerAdaptor

    private fun Int.toPx() = this * mContext.resources.displayMetrics.density

    private fun populateSamplesAsync() {
        Thread {
            val savedWishes = getString(R.string.saved_wishes)
            val samples = resources.getStringArray(R.array.sample_saved)
            samples.forEach {
                mViewModel.insert(
                    Saved(
                        it, System.currentTimeMillis(), SAVED_TYPE_DRAFT, tag = savedWishes
                    )
                )
            }
        }.start()
    }

    private fun setupSavedRecycler() {
        recyclerView.apply {
            layoutManager = LinearLayoutManager(mContext).apply {
                orientation = LinearLayoutManager.VERTICAL
            }
            mAdapter = TagRecyclerAdaptor(mContext)
            adapter = mAdapter
            lifecycleScope.launch {
                mViewModel.tagsFlow.cachedIn(this).collectLatest {
                    mAdapter.submitData(it)
                }
            }
        }
    }

    private fun getSavedArray(tag: String): Array<Saved> {
        return if (intent.type == TYPE_MULTI) {
            val messages =
                intent.getSerializableExtra(EXTRA_MESSAGES) as Array<Message>
            val sender = intent.getStringExtra(EXTRA_SENDER)
            Array(messages.size) {
                val message = messages[it]
                Saved(
                    message.text,
                    System.currentTimeMillis(),
                    if (message.type == MESSAGE_TYPE_INBOX)
                        SAVED_TYPE_RECEIVED else SAVED_TYPE_SENT,
                    tag = tag,
                    path = message.path,
                    sender = sender,
                    messageId = message.id!!,
                )
            }
        } else {
            val text = intent.getStringExtra(EXTRA_MESSAGE_TEXT)
            val uri = intent.data
            arrayOf(
                Saved(
                    text!!,
                    System.currentTimeMillis(),
                    SAVED_TYPE_DRAFT,
                    tag = tag,
                    path = uri?.saveFile(this, System.currentTimeMillis().toString()),
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_saved)
        setupToolbar(toolbar)

        if (mViewModel.dbIsEmpty()) {
            populateSamplesAsync()
        }

        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        setupSavedRecycler()
        if (intent.action == Intent.ACTION_PICK) {
            newTag.visibility = View.VISIBLE

            val mEditText = EditText(this)
            val mLayout = LinearLayout(this).apply {
                mEditText.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                addView(mEditText)
                setPadding(16.toPx().roundToInt(), 0, 16.toPx().roundToInt(), 0)
            }

            mAdapter.setOnClickListener { tag ->
                getSavedArray(tag).forEach {
                    mViewModel.insert(it)
                }
                Toast.makeText(
                    this, getString(R.string.added_to_favorites), Toast.LENGTH_LONG
                ).show()
                finish()
            }

            newTag.setOnClickListener {
                AlertDialog.Builder(this)
                    .setView(mLayout)
                    .setTitle(getString(R.string.new_tag))
                    .setPositiveButton(getString(R.string.add)) { d, _ ->
                        var tag = mEditText.text.toString()
                        tag = if (tag.isBlank()) getString(R.string.saved_not_tagged) else tag
                        getSavedArray(tag).forEach {
                            mViewModel.insert(it)
                        }
                        d.cancel()
                        Toast.makeText(
                            this, getString(R.string.added_to_favorites), Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }.setNegativeButton(getString(R.string.cancel)) { d, _ ->
                        d.cancel()
                        finish()
                    }.create().show()
                mEditText.requestFocus()
                inputManager.showSoftInput(mEditText, 0)
            }
        } else {
            mAdapter.setOnClickListener {
                startActivity(
                    Intent(mContext, TagActivity::class.java)
                        .putExtra(EXTRA_TAG, it)
                )
            }
            newTag.visibility = View.GONE
        }
    }
}