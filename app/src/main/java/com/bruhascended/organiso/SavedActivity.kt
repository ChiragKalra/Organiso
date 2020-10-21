package com.bruhascended.organiso

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.paging.cachedIn
import androidx.recyclerview.widget.LinearLayoutManager
import com.bruhascended.core.constants.EXTRA_TAG
import com.bruhascended.core.constants.SAVED_TYPE_DRAFT
import com.bruhascended.core.db.Saved
import com.bruhascended.organiso.common.setPrefTheme
import com.bruhascended.organiso.common.setupToolbar
import com.bruhascended.organiso.ui.saved.SavedViewModel
import com.bruhascended.organiso.ui.saved.TagActivity
import com.bruhascended.organiso.ui.saved.TagRecyclerAdaptor
import kotlinx.android.synthetic.main.activity_saved.*
import kotlinx.coroutines.flow.collectLatest
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

class SavedActivity : AppCompatActivity() {

    private val mViewModel: SavedViewModel by viewModels()
    private lateinit var mPrefs: SharedPreferences
    private val mContext = this

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
            val mAdapter = TagRecyclerAdaptor(mContext).apply {
                setOnClickListener {
                    startActivity(
                        Intent(mContext, TagActivity::class.java)
                            .putExtra(EXTRA_TAG, it)
                    )
                }
            }
            adapter = mAdapter
            lifecycleScope.launch {
                mViewModel.tagsFlow.cachedIn(this).collectLatest {
                    mAdapter.submitData(it)
                }
            }
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

        setupSavedRecycler()
    }
}