package com.bruhascended.organiso.ui.start

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat
import com.bruhascended.organiso.R
import com.bruhascended.organiso.common.setupToolbar
import kotlinx.android.synthetic.main.activity_policy.*
import org.apache.commons.io.IOUtils

class PolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy)
        setupToolbar(mToolbar, intent.getStringExtra(Intent.EXTRA_TITLE)!!)
        val inputStream = resources.assets.open(intent.getStringExtra(Intent.EXTRA_FROM_STORAGE)!!)
        license.text = SpannableString(
            HtmlCompat.fromHtml(
                IOUtils.toString(inputStream, "UTF-8"),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        )
        license.movementMethod = LinkMovementMethod.getInstance()
    }
}