package com.bruhascended.organiso.backup

import android.app.Application
import android.app.PendingIntent
import android.accounts.Account
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.bruhascended.organiso.R
import com.bruhascended.organiso.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    val preferences = app.getSharedPreferences("sms_backup", 0)
    val busy = MutableLiveData(false)
    val waiting = MutableLiveData(false)
    val googleResolution = MutableLiveData<PendingIntent?>(null)
    val status = MutableLiveData(app.getString(R.string.backup_ready))
    var pendingAction = ""
    var pendingAccount: String? = null
    var stateRestored = false

    val account: String? get() = preferences.getString("drive_account", null)

    fun message(resource: Int) { status.value = getApplication<Application>().getString(resource) }

    fun cancel() {
        waiting.value = false
        pendingAction = ""
        pendingAccount = null
        message(R.string.backup_cancelled)
    }

    fun authorize() {
        val name = pendingAccount ?: return cancel()
        waiting.value = true
        message(R.string.backup_authorizing)
        val request = AuthorizationRequest.builder().setAccount(Account(name, "com.google"))
            .setRequestedScopes(listOf(Scope(DriveBackupClient.SCOPE))).build()
        Identity.getAuthorizationClient(getApplication<Application>()).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) googleResolution.value = result.pendingIntent
                else authorized(result)
            }.addOnFailureListener { authFailed(it) }
    }

    fun authorized(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrEmpty() || !result.grantedScopes.contains(DriveBackupClient.SCOPE)) {
            authFailed(null)
            return
        }
        val name = pendingAccount ?: return cancel()
        preferences.edit().putString("drive_account", name).apply()
        AutomaticBackupScheduler(getApplication<Application>()).refresh(BackupDestination.DRIVE)
        waiting.value = false
        if (pendingAction == "drive") backup(token = token) else message(R.string.backup_ready)
        pendingAction = ""
        pendingAccount = null
    }

    fun authFailed(error: Exception?) {
        val apiError = error as? ApiException
        val detail = apiError?.statusMessage?.replace(Regex("[^\\s@]+@[^\\s@]+"), "[account]")
        if (BuildConfig.DEBUG) android.util.Log.d("DriveAuthorization",
            "Authorization failed: API status=${apiError?.statusCode}; detail=$detail")
        waiting.value = false
        pendingAction = ""
        pendingAccount = null
        val needsRegistration = apiError?.statusCode == 10 ||
            apiError?.statusMessage?.contains("UNREGISTERED_ON_API_CONSOLE") == true
        message(if (needsRegistration)
            R.string.backup_auth_config else R.string.backup_auth_failed)
    }

    fun disconnect() {
        val name = account ?: return
        waiting.value = true
        message(R.string.backup_disconnect_working)
        val request = RevokeAccessRequest.builder().setAccount(Account(name, "com.google"))
            .setScopes(listOf(Scope(DriveBackupClient.SCOPE))).build()
        Identity.getAuthorizationClient(getApplication<Application>()).revokeAccess(request)
            .addOnSuccessListener {
                AutomaticBackupScheduler(getApplication<Application>()).setFrequency(BackupDestination.DRIVE, BackupFrequency.OFF)
                preferences.edit().remove("drive_account").apply()
                waiting.value = false
                message(R.string.backup_disconnected)
            }.addOnFailureListener {
                waiting.value = false
                message(R.string.backup_disconnect_failed)
            }
    }

    fun backup(uri: Uri? = null, token: String? = null) {
        if (busy.value == true) return
        busy.value = true
        message(R.string.backup_working)
        val app = getApplication<Application>()
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val repository = SmsBackupRepository(app)
                    val backup = repository.create()
                    try {
                        if (uri != null) repository.saveToDevice(backup, uri)
                        else DriveBackupClient().upload(backup, requireNotNull(token))
                        backup.messageCount
                    } finally {
                        backup.file.delete()
                    }
                }
                preferences.edit().putLong(if (uri != null) "local_last" else "drive_last",
                    System.currentTimeMillis()).apply()
                status.value = app.getString(R.string.backup_success, count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                message(R.string.backup_permission)
            } catch (e: DriveBackupException) {
                if (e.status == 401 || e.status == 403) {
                    if (token != null) Identity.getAuthorizationClient(app)
                        .clearToken(ClearTokenRequest.builder().setToken(token).build())
                    preferences.edit().remove("drive_account").apply()
                    message(R.string.backup_reconnect)
                } else message(R.string.backup_failed)
            } catch (e: Exception) {
                message(R.string.backup_failed)
            } finally {
                busy.value = false
            }
        }
    }
}
