package com.bruhascended.sms

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.bruhascended.sms.data.labelText
import com.bruhascended.sms.ui.main.CategoryFragment
import kotlinx.android.synthetic.main.activity_conversation.toolbar


class ExtraCategoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_theme", false)
        setTheme(if (dark) R.style.DarkTheme else R.style.LightTheme)
        setContentView(R.layout.activity_extra_category)

        val label = intent.getIntExtra("Type", 4)

        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(labelText[label])

        if (savedInstanceState == null) {
            val newFragment = CategoryFragment.newInstance(label)
            supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, newFragment).commit()
        }
    }
}