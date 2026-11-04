package com.bruhascended.organiso.settings

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bruhascended.core.constants.PREF_DARK_THEME
import com.bruhascended.organiso.R
import com.bruhascended.organiso.SettingsActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Verifies actual dialog text against the rendered window surface, for both dialog APIs. */
@RunWith(Parameterized::class)
class DialogThemeDeviceTest(private val dark: Boolean, private val platform: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "dark={0},platform={1}")
        fun cases() = listOf(arrayOf(false, false), arrayOf(true, false),
            arrayOf(false, true), arrayOf(true, true))
    }

    @Test fun confirmationTextAndButtonsAreReadable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("allowDialogThemeTest") == "true")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        val context = instrumentation.targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val original = preferences.getBoolean(PREF_DARK_THEME, false)
        preferences.edit().putBoolean(PREF_DARK_THEME, dark).commit()
        var activity: SettingsActivity? = null
        var dialog: Dialog? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as SettingsActivity
            val title = context.getString(R.string.delete_all_otps_query)
            val owner = activity
            instrumentation.runOnMainSync {
                dialog = if (platform) android.app.AlertDialog.Builder(owner)
                    .setTitle(title).setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null).show()
                else androidx.appcompat.app.AlertDialog.Builder(owner)
                    .setTitle(title).setPositiveButton(R.string.ok, null)
                    .setNegativeButton(R.string.cancel, null).show()
            }
            instrumentation.waitForIdleSync()
            val variant = "${if (platform) "platform" else "appcompat"}-${if (dark) "dark" else "light"}"
            UiDevice.getInstance(instrumentation).takeScreenshot(File(context.filesDir, "dialog-$variant.png"))
            instrumentation.runOnMainSync {
                val current = requireNotNull(dialog)
                val decor = requireNotNull(current.window).decorView
                val drawable = requireNotNull(decor.background)
                val oldBounds = android.graphics.Rect(drawable.bounds)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, 512, 512)
                drawable.draw(Canvas(bitmap))
                drawable.bounds = oldBounds
                val background = bitmap.getPixel(256, 256)
                bitmap.recycle()
                assertTrue("Dialog surface must be opaque", Color.alpha(background) == 255)
                fun texts(view: View): List<TextView> = when (view) {
                    is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                    is TextView -> listOf(view)
                    else -> emptyList()
                }
                val labels = texts(decor)
                val titleView = labels.single { it.text.toString() == title }
                val controls = listOf(titleView, current.findViewById<TextView>(android.R.id.button1),
                    current.findViewById<TextView>(android.R.id.button2))
                val ratios = controls.map { control ->
                    requireNotNull(control)
                    val contrast = ColorUtils.calculateContrast(control.currentTextColor, background)
                    "${control.text}: $contrast".also {
                        assertTrue("$variant contrast below 4.5:1: $it", contrast >= 4.5)
                    }
                }
                File(context.filesDir, "dialog-$variant-contrast.txt").writeText(
                    "surface=${Integer.toHexString(background)}\n" + ratios.joinToString("\n"))
            }
        } finally {
            instrumentation.runOnMainSync { dialog?.dismiss(); activity?.finish() }
            preferences.edit().putBoolean(PREF_DARK_THEME, original).commit()
        }
    }
}
