package com.bruhascended.organiso.backup

import android.Manifest
import android.accounts.Account
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bruhascended.organiso.R
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.util.concurrent.TimeUnit

/** No UI, no SMS-role requests, and no changes to the SMS provider. */
class AutomaticBackupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val destination = inputData.getString("destination")?.let {
            runCatching { BackupDestination.valueOf(it) }.getOrNull()
        } ?: return Result.failure()
        val generation = inputData.getString("generation") ?: return Result.failure()
        val scheduler = AutomaticBackupScheduler(applicationContext)
        val prefs = scheduler.preferences
        if (!scheduler.isCurrent(destination, generation) || isStopped) return Result.success()
        fun status(text: String) {
            if (scheduler.isCurrent(destination, generation)) prefs.edit()
                .putString(AutomaticBackupScheduler.key(destination, "status"), text).apply()
        }
        var backup: SmsBackup? = null
        var token: String? = null
        var retry = false
        prefs.edit().putBoolean(AutomaticBackupScheduler.key(destination, "running"), true).apply()
        try {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException("SMS read permission required")
            if (destination == BackupDestination.DRIVE) {
                val name = prefs.getString("drive_account", null) ?: throw ReconnectRequired()
                val request = AuthorizationRequest.builder().setAccount(Account(name, "com.google"))
                    .setRequestedScopes(listOf(Scope(DriveBackupClient.SCOPE))).build()
                val result = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(request), 30, TimeUnit.SECONDS)
                // Never launch account/consent dialogs from background work.
                if (result.hasResolution() || result.accessToken.isNullOrEmpty() || !result.grantedScopes.contains(DriveBackupClient.SCOPE))
                    throw ReconnectRequired()
                token = result.accessToken
            }
            status(applicationContext.getString(R.string.backup_working))
            val repository = SmsBackupRepository(applicationContext)
            backup = repository.create()
            if (isStopped || !scheduler.isCurrent(destination, generation)) return Result.success()
            if (destination == BackupDestination.LOCAL) {
                val folder = prefs.getString("auto_local_folder", null) ?: throw SecurityException("Folder required")
                repository.saveToFolder(backup, Uri.parse(folder))
            } else DriveBackupClient().upload(backup, requireNotNull(token))
            if (scheduler.isCurrent(destination, generation)) prefs.edit()
                .putLong("${destination.key}_last", System.currentTimeMillis())
                .putLong(AutomaticBackupScheduler.key(destination, "last"), System.currentTimeMillis())
                .putInt(AutomaticBackupScheduler.key(destination, "count"), backup.messageCount)
                .apply()
            status(applicationContext.getString(R.string.backup_success, backup.messageCount))
        } catch (e: ReconnectRequired) {
            status(applicationContext.getString(R.string.backup_auto_reconnect))
        } catch (e: SecurityException) {
            status(applicationContext.getString(R.string.backup_auto_permission))
        } catch (e: DriveBackupException) {
            if (e.status == 401 || e.status == 403) {
                token?.let { Identity.getAuthorizationClient(applicationContext)
                    .clearToken(ClearTokenRequest.builder().setToken(it).build()) }
                status(applicationContext.getString(R.string.backup_auto_reconnect))
            } else {
                retry = (e.status == 429 || e.status >= 500) && runAttemptCount < 2
                status(applicationContext.getString(if (retry) R.string.backup_auto_retry else R.string.backup_failed))
            }
        } catch (e: Exception) {
            retry = runAttemptCount < 2
            status(applicationContext.getString(if (retry) R.string.backup_auto_retry else R.string.backup_failed))
        } finally {
            backup?.file?.delete()
            if (scheduler.isCurrent(destination, generation)) prefs.edit()
                .putBoolean(AutomaticBackupScheduler.key(destination, "running"), false).apply()
        }
        if (isStopped || !scheduler.isCurrent(destination, generation)) return Result.success()
        if (retry) return Result.retry()
        if (inputData.getBoolean("scheduled", true)) scheduler.scheduleNext(destination, generation,
            inputData.getLong("scheduled_at", System.currentTimeMillis()))
        // The outcome is stored per destination; a failed backup must not break the next cadence.
        return Result.success()
    }

    private class ReconnectRequired : IOException()
}
