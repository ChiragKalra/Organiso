package com.bruhascended.organiso.backup

import android.content.Intent
import android.app.Application
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.lifecycle.Observer
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.UiScrollable
import com.bruhascended.organiso.SettingsActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.regex.Pattern
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** These tests only read real SMS. They do not send, insert, restore, or delete messages. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BackupDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun clickText(text: String) {
        device.waitForIdle(2_000)
        val target = device.findObject(UiSelector().text(text))
        if (!target.waitForExists(2_000)) {
            UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text))
        }
        assertTrue("Expected visible control: $text", target.waitForExists(5_000))
        assertTrue(target.click())
    }

    private fun smsDigest(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.query(Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "address", "body", "date", "date_sent", "type", "read", "seen", "sub_id"),
            null, null, "_id ASC")!!.use { cursor ->
            while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) {
                val bytes = (cursor.getString(column) ?: "<null>").toByteArray(Charsets.UTF_8)
                digest.update(bytes.size.toString().toByteArray())
                digest.update(0.toByte())
                digest.update(bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun a_createVerifiedSafetyBackup() {
        val before = smsDigest()
        val backup = SmsBackupRepository(context).create()
        try {
            assertTrue("Expected the real user's SMS history", backup.messageCount > 0)
            // Stream the large fixture too, so test allocations do not distort UI responsiveness.
            var count = 0
            var declaredCount = -1
            var format = ""
            var version = 0
            JsonReader(backup.file.reader(Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) when (reader.nextName()) {
                    "format" -> format = reader.nextString()
                    "version" -> version = reader.nextInt()
                    "messageCount" -> declaredCount = reader.nextInt()
                    "sms" -> {
                        reader.beginArray()
                        context.contentResolver.query(Telephony.Sms.CONTENT_URI,
                            arrayOf("_id", "address", "body", "date", "date_sent", "type", "read", "seen", "sub_id"),
                            null, null, "_id ASC")!!.use { cursor ->
                            while (reader.hasNext()) {
                                assertTrue(cursor.moveToNext())
                                val message = HashMap<String, String?>()
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val key = reader.nextName()
                                    message[key] = if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull(); null
                                    } else reader.nextString()
                                }
                                reader.endObject()
                                for (column in 0 until cursor.columnCount) {
                                    val key = cursor.getColumnName(column)
                                    assertTrue("Exported $key differs for SMS ID ${cursor.getLong(0)}",
                                        message.containsKey(key) && message[key] == cursor.getString(column))
                                }
                                count++
                            }
                            assertFalse(cursor.moveToNext())
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
                reader.endObject()
            }
            assertEquals("organiso-sms-backup", format)
            assertEquals(1, version)
            assertEquals(backup.messageCount, count)
            assertEquals(count, declaredCount)
            val destination = File(context.filesDir, "verified-sms-safety-backup.json")
            backup.file.copyTo(destination, overwrite = true)
            assertEquals(backup.sha256, destination.inputStream().use { SmsBackupRepository.digest(it) })
            File(context.filesDir, "verified-sms-safety-backup-manifest.json").writeText(JSONObject()
                .put("count", backup.messageCount).put("sha256", backup.sha256)
                .put("sourceFingerprint", before).toString(2))
            assertEquals("Backing up must not change SMS", before, smsDigest())
        } finally { backup.file.delete() }
    }

    @Test fun b_driveUploadVerifiesBytesAndPreservesOlderBackups() {
        val file = File.createTempFile("drive-unit-", ".json", context.cacheDir)
        try {
            file.writeText("{\"sms\":[{\"body\":\"test \\n ☃\"}],\"messageCount\":1}")
            val backup = SmsBackup(file, 1, file.inputStream().use { SmsBackupRepository.digest(it) },
                file.inputStream().use { SmsBackupRepository.digest(it, "MD5") })
            val requests = mutableListOf<String>()
            val transport = object : DriveTransport {
                override fun request(url: String, method: String, token: String, contentType: String,
                                     length: Long, write: (OutputStream) -> Unit): DriveResponse {
                    requests.add(method)
                    val output = ByteArrayOutputStream().also(write).toByteArray()
                    assertEquals(length, output.size.toLong())
                    return if (method == "POST") {
                        val metadata = JSONObject(String(output, Charsets.UTF_8))
                        assertEquals("appDataFolder", metadata.getJSONArray("parents").getString(0))
                        assertEquals(backup.sha256, metadata.getJSONObject("appProperties").getString("sha256"))
                        DriveResponse(200, "", "https://www.googleapis.com/upload/drive/v3/files?upload_id=test")
                    } else {
                        assertArrayEquals(file.readBytes(), output)
                        DriveResponse(200, JSONObject().put("id", "new-backup-id")
                            .put("size", length.toString()).put("md5Checksum", backup.md5).toString())
                    }
                }
            }
            assertEquals("new-backup-id", DriveBackupClient(transport).upload(backup, "fake-token"))
            assertEquals(listOf("POST", "PUT"), requests)
        } finally { file.delete() }
    }

    @Test fun c_driveRejectsBadChecksumErrorsAndUntrustedSessions() {
        val file = File.createTempFile("drive-unit-", ".json", context.cacheDir).apply { writeText("{}") }
        val backup = SmsBackup(file, 0, "sha", "expected-md5")
        try {
            for (failure in listOf("checksum", "401", "403", "500", "redirect")) {
                val transport = object : DriveTransport {
                    override fun request(url: String, method: String, token: String, contentType: String,
                                         length: Long, write: (OutputStream) -> Unit): DriveResponse {
                        if (method == "POST") return DriveResponse(200, "", if (failure == "redirect")
                            "https://untrusted.example/upload" else "https://www.googleapis.com/upload/test")
                        if (failure != "checksum") return DriveResponse(failure.toInt(), "")
                        return DriveResponse(200, "{\"id\":\"id\",\"size\":\"2\",\"md5Checksum\":\"wrong\"}")
                    }
                }
                try {
                    DriveBackupClient(transport).upload(backup, "fake-token")
                    fail("Expected failure: $failure")
                } catch (expected: IOException) { /* Must never claim success. */ }
            }
        } finally { file.delete() }
    }

    private fun openBackup() {
        device.wakeUp()
        device.pressMenu()
        instrumentation.startActivitySync(Intent(context, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        instrumentation.waitForIdleSync()
        if (!device.wait(Until.hasObject(By.text("Backup")), 15_000)) {
            device.dumpWindowHierarchy(File(context.filesDir, "e2e-failure-ui.xml"))
            device.takeScreenshot(File(context.filesDir, "e2e-failure.png"))
            fail("Settings did not show Backup; see private e2e-failure artifacts")
        }
        clickText("Backup")
        assertTrue(device.wait(Until.hasObject(By.text("Back up to this device")), 5_000))
    }

    @Test fun d_localBackupCancellationDoesNotRecordSuccess() {
        val prefs = context.getSharedPreferences("sms_backup", 0)
        val last = prefs.getLong("local_last", 0)
        openBackup()
        clickText("Back up to this device")
        assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))), 10_000))
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.text("Backup cancelled. No backup was recorded.")), 5_000))
        assertEquals(last, prefs.getLong("local_last", 0))
    }

    @Test fun e_localBackupThroughSystemFilePicker() {
        val prefs = context.getSharedPreferences("sms_backup", 0)
        val last = prefs.getLong("local_last", 0)
        openBackup()
        clickText("Back up to this device")
        assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile("com\\.(google\\.android|android)\\.documentsui"))), 10_000))
        val roots = device.findObject(By.desc("Show roots"))
        if (roots != null) {
            roots.click()
            val downloads = device.findObject(UiSelector().textMatches("(?i)downloads?"))
            if (downloads.waitForExists(3_000)) downloads.click()
            else device.pressBack() // Keep the local directory chosen by the system picker.
        }
        val filename = device.findObject(UiSelector().className("android.widget.EditText"))
        assertTrue(filename.waitForExists(5_000))
        filename.setText("Organiso-E2E-${System.currentTimeMillis()}.json")
        val save = device.findObject(UiSelector().textMatches("(?i)save"))
        assertTrue(save.waitForExists(5_000))
        assertTrue(save.click())
        val count = JSONObject(File(context.filesDir, "verified-sms-safety-backup-manifest.json").readText()).getInt("count")
        assertTrue(device.wait(Until.hasObject(By.text("Backed up $count SMS messages. Verified successfully.")), 60_000))
        assertTrue("Only a verified backup updates last-success time", prefs.getLong("local_last", 0) > last)
    }

    @Test fun f_cancelGoogleConnectionKeepsDriveDisabled() {
        val prefs = context.getSharedPreferences("sms_backup", 0)
        assertNull("Use a fresh emulator without an existing Drive connection", prefs.getString("drive_account", null))
        openBackup()
        clickText("Google Drive connection")
        assertTrue("Google account selection should open", device.wait(Until.hasObject(By.pkg("com.google.android.gms")), 15_000))
        // No account credentials are entered by the test.
        repeat(3) {
            if (device.hasObject(By.pkg("com.google.android.gms"))) device.pressBack()
        }
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
        val list = UiScrollable(UiSelector().scrollable(true))
        if (list.exists()) list.scrollToBeginning(5)
        assertTrue(device.wait(Until.hasObject(By.text("Backup cancelled. No backup was recorded.")), 10_000))
        assertNull(prefs.getString("drive_account", null))
        assertEquals(0L, prefs.getLong("drive_last", 0))
    }

    @Test fun g_failedDestinationDoesNotRecordSuccessOrKeepTemporarySms() {
        val preferences = context.getSharedPreferences("sms_backup", 0)
        val last = preferences.getLong("local_last", 0)
        val done = CountDownLatch(1)
        lateinit var model: BackupViewModel
        val observer = Observer<Boolean> { busy -> if (!busy) done.countDown() }
        instrumentation.runOnMainSync {
            model = BackupViewModel(context.applicationContext as Application)
            model.backup(uri = Uri.parse("content://organiso.invalid.destination/backup.json"))
            model.busy.observeForever(observer)
        }
        assertTrue("Failed destination should finish with an error", done.await(60, TimeUnit.SECONDS))
        instrumentation.runOnMainSync {
            model.busy.removeObserver(observer)
            assertEquals(context.getString(com.bruhascended.organiso.R.string.backup_failed), model.status.value)
        }
        assertEquals(last, preferences.getLong("local_last", 0))
        assertEquals(0, File(context.cacheDir, "sms-backups").listFiles()?.size ?: 0)
    }

    @Test fun z_smsStillUnchangedAfterBackupTests() {
        val manifest = JSONObject(File(context.filesDir, "verified-sms-safety-backup-manifest.json").readText())
        assertEquals("SMS must survive all tests unchanged", manifest.getString("sourceFingerprint"), smsDigest())
    }
}
