package com.bruhascended.organiso.backup

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.bruhascended.core.constants.PREF_DARK_THEME
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.Condition
import com.bruhascended.organiso.SettingsActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Explicit opt-in only. Does not acquire the SMS role or write to the SMS provider. */
@RunWith(AndroidJUnit4::class)
class LiveDriveDeviceTest {
    @Test fun verifyLiveDriveWithExistingAccount() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        require(InstrumentationRegistry.getArguments().getString("allowLiveDrive") == "true")
        val context = instrumentation.targetContext
        if (InstrumentationRegistry.getArguments().getString("darkTheme") == "true")
            PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(PREF_DARK_THEME, true).commit()
        val device = UiDevice.getInstance(instrumentation)
        fun step(value: String) = File(context.filesDir, "live-drive-step.txt").writeText(value)
        val originalDefault = Telephony.Sms.getDefaultSmsPackage(context)
        fun click(text: String) {
            val target = device.findObject(UiSelector().text(text))
            val list = UiScrollable(UiSelector().scrollable(true))
            if (!target.waitForExists(2_000) && list.exists()) list.scrollIntoView(UiSelector().text(text))
            assertTrue("Missing $text", target.waitForExists(5_000))
            target.click()
        }
        device.wakeUp()
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        if (device.hasObject(By.text("Choose an account")) ||
            device.hasObject(By.text("Choose account for $appLabel"))) device.pressBack()
        step("Opening backup settings")
        instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
        click("Backup")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            click("Back up to this device")
            // Exercise the normal runtime permission flow; do not change the default SMS app.
            val allow = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_allow_button"))), 3_000)
            allow?.click()
            assertTrue("Local picker should open after granting read access", device.wait(Until.hasObject(
                By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))), 15_000))
            device.pressBack()
            repeat(3) {
                if (!device.wait(Until.hasObject(By.pkg(context.packageName)), 1_000)) device.pressBack()
            }
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5_000))
        }
        step("Creating read-only safety backup")
        val previousBackup = File(context.filesDir, "live-drive-preflight-sms.json")
        val reuse = InstrumentationRegistry.getArguments().getString("reuseSafetyBackup") == "true" && previousBackup.exists()
        if (reuse) {
            val manifest = JSONObject(File(context.filesDir, "live-drive-preflight.json").readText())
            assertEquals(manifest.getString("sha256"), previousBackup.inputStream().use { SmsBackupRepository.digest(it) })
        } else {
        val backup = SmsBackupRepository(context).create()
        try {
            val destination = File(context.filesDir, "live-drive-preflight-sms.json")
            backup.file.copyTo(destination, overwrite = true)
            assertEquals(backup.sha256, destination.inputStream().use { SmsBackupRepository.digest(it) })
            File(context.filesDir, "live-drive-preflight.json").writeText(JSONObject()
                .put("count", backup.messageCount).put("sha256", backup.sha256).toString(2))
        } finally { backup.file.delete() }
        }
        step("Choosing Google account")
        click("Google Drive connection")
        // Choose an explicitly supplied account, otherwise the first account in Android's chooser.
        val account = InstrumentationRegistry.getArguments().getString("account")
        val accountSelector = if (account != null) By.text(account)
            else By.text(Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        val choice = device.wait(Until.findObject(accountSelector), 10_000)
        device.takeScreenshot(File(context.filesDir, "drive-account-picker.png"))
        choice?.click()
        val confirm = device.findObject(UiSelector().textMatches("(?i)ok").enabled(true))
        if (confirm.waitForExists(2_000)) confirm.click()
        step("Waiting for Google authorization")
        // Only approve the specific app-data scope requested by this opt-in test.
        if (device.wait(Until.hasObject(By.textContains("configuration data")), 5_000)) {
            val consent = device.findObject(UiSelector().textMatches("(?i)continue|allow").enabled(true))
            if (consent.waitForExists(3_000)) consent.click()
        }
        val prefs = context.getSharedPreferences("sms_backup", 0)
        val connected = device.wait(object : Condition<UiDevice, Boolean> {
            override fun apply(device: UiDevice): Boolean? {
                if (prefs.getString("drive_account", null) != null) return true
                val configError = device.hasObject(By.textContains("not configured for this app build"))
                val authError = device.hasObject(By.textContains("Google Drive could not connect"))
                return if (configError || authError) true else null
            }
        }, 20_000)
        if (connected == null || prefs.getString("drive_account", null) == null) {
            device.dumpWindowHierarchy(File(context.filesDir, "live-drive-ui.xml"))
            device.takeScreenshot(File(context.filesDir, "live-drive-ui.png"))
            File(context.filesDir, "live-drive-result.json").writeText(JSONObject()
                .put("connected", false).put("defaultSmsAppUnchanged", originalDefault == Telephony.Sms.getDefaultSmsPackage(context))
                .put("reason", if (device.hasObject(By.textContains("not configured for this app build"))) "android-oauth-registration" else "consent-or-authorization-required")
                .toString(2))
            fail("Google authorization did not complete. See private live-drive-result.json and UI artifacts.")
        }
        click("Back up to Google Drive")
        step("Uploading and verifying SMS backup")
        val list = UiScrollable(UiSelector().scrollable(true))
        if (list.exists()) list.scrollToBeginning(5)
        val success = device.wait(Until.hasObject(By.textContains("Verified successfully.")), 120_000)
        assertTrue("Drive backup must finish and verify", success)
        assertTrue(prefs.getLong("drive_last", 0) > 0)
        assertEquals("Never change the real phone's default SMS app", originalDefault, Telephony.Sms.getDefaultSmsPackage(context))
        File(context.filesDir, "live-drive-result.json").writeText(JSONObject()
            .put("connected", true).put("uploadedAndVerified", true).put("defaultSmsAppUnchanged", true).toString(2))
    }
}
