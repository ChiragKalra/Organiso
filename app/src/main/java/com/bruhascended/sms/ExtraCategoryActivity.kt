package com.bruhascended.sms

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.ui.main.CategoryFragment


class ExtraCategoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = intent.getIntExtra("Type", 4)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(labelText[label])

        if (savedInstanceState == null) {
            val newFragment = CategoryFragment.newInstance(label)
            val ft = supportFragmentManager.beginTransaction()
            ft.add(android.R.id.content, newFragment).commit()
        }
    }
}