package com.bruhascended.organiso.backup

import android.app.role.RoleManager
import android.os.Build
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bruhascended.core.constants.MESSAGE_TYPE_INBOX
import com.bruhascended.core.constants.deleteSMS
import com.bruhascended.core.constants.isDefaultSmsApp
import com.bruhascended.core.constants.saveSms
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in regression: inserts and deletes only its own synthetic row on the dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class DefaultSmsRoleDeviceTest {
    @Test fun heldSmsRoleCanSaveAndDeleteItsOwnProviderRow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        require(InstrumentationRegistry.getArguments().getString("allowFgsDemo") == "true")
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        require(UiDevice.getInstance(instrumentation).executeShellCommand("getprop ro.boot.qemu.avd_name")
            .trim() == "Organiso_Play_FGS_Demo")
        assertTrue(context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS))
        assertTrue(context.isDefaultSmsApp())
        val id = context.saveSms("DEMO-ROLE-TEST", "Synthetic default-SMS regression test", MESSAGE_TYPE_INBOX)
        assertNotNull("A held SMS role must be able to save the row", id)
        val uri = android.content.ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id!!.toLong())
        try {
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { assertEquals(1, it.count) }
            context.deleteSMS(id)
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { assertEquals(0, it.count) }
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
