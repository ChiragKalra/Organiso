package com.bruhascended.organiso.backup

import android.content.ContentValues
import android.os.Build
import android.app.role.RoleManager
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Fixture import is intentionally confined to instrumentation and a dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class EmulatorSmsSeedTest {
    @Test fun seedOnlyAnEmptyEmulator() {
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
            "Refusing to seed SMS on a physical device"
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("allowEmulatorSmsSeed") == "true") { "Explicit emulator seed flag required" }
        val fixture = JSONObject(File(context.filesDir, "emulator-sms-fixture.json").readText())
        val messages = fixture.getJSONArray("sms")
        assertEquals(messages.length(), fixture.getInt("messageCount"))
        assertTrue("The emulator must grant the SMS role to the staging app",
            context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS))
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), null, null, null)!!.use {
            assertEquals("Use a fresh dedicated emulator; never clear an existing SMS store", 0, it.count)
        }
        val supported = setOf("_id", "address", "body", "date", "date_sent", "protocol", "read", "status",
            "type", "reply_path_present", "subject", "service_center", "locked", "sub_id", "error_code", "seen")
        val batch = ArrayList<ContentValues>(250)
        for (index in 0 until messages.length()) {
            val sms = messages.getJSONObject(index)
            val values = ContentValues()
            for (key in supported) {
                if (!sms.has(key) || sms.isNull(key)) values.putNull(key)
                else when (val value = sms.get(key)) {
                    is Number -> values.put(key, value.toLong())
                    else -> values.put(key, value.toString())
                }
            }
            batch.add(values)
            if (batch.size == 250 || index == messages.length() - 1) {
                assertEquals(batch.size, context.contentResolver.bulkInsert(Telephony.Sms.CONTENT_URI, batch.toTypedArray()))
                batch.clear()
            }
        }
        // Verify every imported message, including Unicode and multiline bodies.
        val expected = (0 until messages.length()).associate { index ->
            val message = messages.getJSONObject(index)
            message.getLong("_id") to message
        }
        context.contentResolver.query(Telephony.Sms.CONTENT_URI,
            arrayOf("_id", "address", "body", "date", "date_sent", "type", "read", "seen", "sub_id"),
            null, null, "_id ASC")!!.use { cursor ->
            assertEquals(messages.length(), cursor.count)
            while (cursor.moveToNext()) {
                val original = expected[cursor.getLong(0)]!!
                for (column in 1 until cursor.columnCount) {
                    val key = cursor.getColumnName(column)
                    val expectedValue = if (original.isNull(key)) null else original.get(key).toString()
                    assertTrue("Imported $key differs for SMS ID ${cursor.getLong(0)}",
                        expectedValue == cursor.getString(column))
                }
            }
        }
    }
}
