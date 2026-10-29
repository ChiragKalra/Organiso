package com.bruhascended.organiso.settings

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.bruhascended.organiso.R
import com.bruhascended.organiso.BuildConfig
import com.bruhascended.organiso.backup.BackupViewModel
import com.bruhascended.organiso.backup.SmsBackupRepository
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.api.ApiException
import java.util.Date

class BackupFragment : PreferenceFragmentCompat() {
    private val model: BackupViewModel by viewModels()
    private val backupPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        view?.post { refresh() }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startPendingAction() else {
            model.waiting.value = false
            model.pendingAction = ""
            model.message(R.string.backup_permission)
            refresh()
        }
    }

    private val document = registerForActivityResult(object : ActivityResultContracts.CreateDocument("application/json") {
        override fun createIntent(context: Context, input: String): Intent =
            super.createIntent(context, input).putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }) { uri ->
        model.waiting.value = false
        if (uri != null) model.backup(uri = uri) else model.message(R.string.backup_cancelled)
        model.pendingAction = ""
        refresh()
    }

    private val chooseAccount = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (BuildConfig.DEBUG) android.util.Log.d("DriveAuthorization",
            "Account picker result=${result.resultCode}, hasAccount=${!name.isNullOrEmpty()}")
        if (result.resultCode == Activity.RESULT_OK && !name.isNullOrEmpty()) {
            model.pendingAccount = name
            model.authorize()
        } else model.cancel()
    }

    private val authorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (BuildConfig.DEBUG) android.util.Log.d("DriveAuthorization",
            "Google resolution result=${result.resultCode}, hasData=${result.data != null}")
        if (result.resultCode != Activity.RESULT_OK && result.data == null) model.cancel()
        else try {
            model.authorized(Identity.getAuthorizationClient(requireActivity()).getAuthorizationResultFromIntent(result.data))
        } catch (e: ApiException) {
            if (e.statusCode == 16 || e.statusCode == 12501) model.cancel()
            else model.authFailed(e)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // A retained ViewModel may have finished a task while the view was being recreated.
        if (!model.stateRestored) {
            if (savedInstanceState != null) model.waiting.value = savedInstanceState.getBoolean("waiting")
            model.pendingAction = savedInstanceState?.getString("pendingAction") ?: model.pendingAction
            model.pendingAccount = savedInstanceState?.getString("pendingAccount") ?: model.pendingAccount
            model.stateRestored = true
        }
        setPreferencesFromResource(R.xml.backup_preferences, rootKey)
        findPreference<Preference>("automatic_backups")!!.fragment = AutomaticBackupFragment::class.qualifiedName
        findPreference<Preference>("backup_local")!!.setOnPreferenceClickListener {
            begin("local")
            true
        }
        findPreference<SwitchPreferenceCompat>("backup_drive_connection")!!.setOnPreferenceChangeListener { _, value ->
            if (value == true) begin("connect") else model.disconnect()
            false // Only reflect a connection after Google has actually authorized it.
        }
        findPreference<Preference>("backup_drive_now")!!.setOnPreferenceClickListener {
            begin("drive")
            true
        }
        refresh()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.busy.observe(viewLifecycleOwner) { refresh() }
        model.status.observe(viewLifecycleOwner) { refresh() }
        model.waiting.observe(viewLifecycleOwner) { refresh() }
        model.googleResolution.observe(viewLifecycleOwner) { pending ->
            if (pending != null) {
                model.googleResolution.value = null
                authorization.launch(IntentSenderRequest.Builder(pending.intentSender).build())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStart() {
        super.onStart()
        model.preferences.registerOnSharedPreferenceChangeListener(backupPreferencesListener)
    }

    override fun onStop() {
        model.preferences.unregisterOnSharedPreferenceChangeListener(backupPreferencesListener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("waiting", model.waiting.value == true)
        outState.putString("pendingAction", model.pendingAction)
        outState.putString("pendingAccount", model.pendingAccount)
        super.onSaveInstanceState(outState)
    }

    private fun begin(action: String) {
        if (model.waiting.value == true || model.busy.value == true) return
        model.pendingAction = action
        model.waiting.value = true
        refresh()
        // Connecting alone doesn't read messages and doesn't need SMS permission.
        if (action == "connect" || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS)
            == PackageManager.PERMISSION_GRANTED) startPendingAction()
        else permission.launch(Manifest.permission.READ_SMS)
    }

    private fun startPendingAction() {
        when (model.pendingAction) {
            "local" -> document.launch(SmsBackupRepository.filename())
            "connect" -> chooseAccount.launch(AccountPicker.newChooseAccountIntent(
                AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google"))
                    .setAlwaysShowAccountPicker(true)
                    .build()))
            "drive" -> {
                model.pendingAccount = model.account
                model.authorize()
            }
        }
    }

    private fun refresh() {
        if (!isAdded || preferenceScreen == null) return
        val enabled = model.waiting.value != true && model.busy.value != true
        val account = model.account
        findPreference<Preference>("backup_local")!!.isEnabled = enabled
        findPreference<SwitchPreferenceCompat>("backup_drive_connection")!!.apply {
            isEnabled = enabled
            isChecked = account != null
            summary = if (account != null) getString(R.string.backup_drive_connected, account)
                else getString(R.string.backup_drive_disconnected)
        }
        findPreference<Preference>("backup_drive_now")!!.isEnabled = enabled && account != null
        for (kind in listOf("local", "drive")) {
            val time = model.preferences.getLong("${kind}_last", 0)
            findPreference<Preference>("backup_${kind}_last")!!.summary = if (time == 0L)
                getString(R.string.backup_never) else DateFormat.getMediumDateFormat(requireContext()).format(Date(time)) +
                " " + DateFormat.getTimeFormat(requireContext()).format(Date(time))
        }
        findPreference<Preference>("backup_status")!!.summary = model.status.value
    }
}
