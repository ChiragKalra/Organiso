package com.bruhascended.organiso

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.core.constants.*
import com.bruhascended.organiso.settings.InterfaceFragment.Companion.setPrefTheme
import com.bruhascended.organiso.ui.main.CategoryFragment
import kotlinx.android.synthetic.main.activity_conversation.toolbar

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

class ExtraCategoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPrefTheme()
        setContentView(R.layout.activity_extra_category)

        val label = intent.getIntExtra(EXTRA_LABEL, LABEL_SPAM)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = resources.getStringArray(R.array.labels)[label]

        if (savedInstanceState == null) {
            val newFragment = CategoryFragment.newInstance(label)
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, newFragment).commit()
        }
    }
}