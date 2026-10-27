package com.bruhascended.organiso

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

abstract class BaseActivity : AppCompatActivity() {
    private var contentPadding: Rect? = null
    private var statusBarInset = 0
    private var statusBarBackground: ColorDrawable? = null
    protected open val bottomInsetReductionDp = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        val content = findViewById<View>(android.R.id.content)
        val initialPadding = contentPadding ?: Rect(
            content.paddingLeft,
            content.paddingTop,
            content.paddingRight,
            content.paddingBottom
        ).also { contentPadding = it }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            statusBarInset = systemBars.top
            val bottomInset = (systemBars.bottom -
                (bottomInsetReductionDp * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
            view.setPadding(
                initialPadding.left + systemBars.left,
                initialPadding.top + systemBars.top,
                initialPadding.right + systemBars.right,
                initialPadding.bottom + bottomInset
            )
            updateStatusBarBackground(view)
            windowInsets
        }
        content.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            updateStatusBarBackground(view)
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun updateStatusBarBackground(content: View) {
        val background = statusBarBackground ?: ColorDrawable(
            ContextCompat.getColor(this, R.color.colorPrimaryDark)
        ).also {
            statusBarBackground = it
            content.overlay.add(it)
        }
        background.setBounds(0, 0, content.width, statusBarInset)
        content.invalidate()
    }
}
