package com.bruhascended.organiso.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bruhascended.organiso.R
import com.bruhascended.organiso.backup.AutomaticBackupScheduler
import com.bruhascended.organiso.backup.BackupDestination
import com.bruhascended.organiso.backup.BackupFrequency
import java.util.Date

class AutomaticBackupFragment : PreferenceFragmentCompat() {
    private lateinit var scheduler: AutomaticBackupScheduler
    private var pendingDestination: BackupDestination? = null
    private var pendingFrequency: BackupFrequency? = null
    private val activeWork = HashMap<String, Boolean>()
    private val runningWork = HashMap<BackupDestination, Boolean>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        view?.post { refresh() }
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val destination = pendingDestination
        val frequency = pendingFrequency
        if (granted && destination != null && frequency != null) configure(destination, frequency)
        else toast(R.string.backup_permission)
    }

    private val folder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                val flags = result.data!!.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 && flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
                requireContext().contentResolver.takePersistableUriPermission(uri, flags)
                scheduler.preferences.edit().putString("auto_local_folder", uri.toString()).apply()
                val frequency = pendingFrequency
                if (frequency != null) scheduler.setFrequency(BackupDestination.LOCAL, frequency)
                else scheduler.refresh(BackupDestination.LOCAL)
            } catch (e: Exception) { toast(R.string.backup_auto_permission) }
        }
        pendingDestination = null
        pendingFrequency = null
        refresh()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        scheduler = AutomaticBackupScheduler(requireContext())
        pendingDestination = savedInstanceState?.getString("pending_destination")?.let { BackupDestination.valueOf(it) }
        pendingFrequency = savedInstanceState?.getString("pending_frequency")?.let { BackupFrequency.from(it) }
        setPreferencesFromResource(R.xml.automatic_backup_preferences, rootKey)
        for (destination in BackupDestination.values()) {
            findPreference<ListPreference>(AutomaticBackupScheduler.key(destination, "frequency"))!!
                .setOnPreferenceChangeListener { _, value ->
                    configure(destination, BackupFrequency.from(value as String))
                    false
                }
            findPreference<Preference>(AutomaticBackupScheduler.key(destination, "now"))!!
                .setOnPreferenceClickListener {
                    scheduler.runNow(destination)
                    true
                }
        }
        findPreference<Preference>("auto_local_folder")!!.setOnPreferenceClickListener {
            pendingFrequency = null
            chooseFolder()
            true
        }
        findPreference<SwitchPreferenceCompat>("auto_drive_unmetered")!!.setOnPreferenceChangeListener { _, value ->
            scheduler.preferences.edit().putBoolean("auto_drive_unmetered", value as Boolean).apply()
            scheduler.refresh(BackupDestination.DRIVE)
            refresh()
            false
        }
        refresh()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        for (destination in BackupDestination.values()) for (suffix in listOf("", "_now")) {
            val name = AutomaticBackupScheduler.workName(destination) + suffix
            val manager = WorkManager.getInstance(requireContext())
            val workInfo = if (suffix.isEmpty()) manager.getWorkInfosByTagLiveData(name)
                else manager.getWorkInfosForUniqueWorkLiveData(name)
            workInfo.observe(viewLifecycleOwner) { work ->
                activeWork[name] = work.any { !it.state.isFinished }
                if (suffix.isEmpty()) runningWork[destination] = work.any { it.state == WorkInfo.State.RUNNING }
                refresh()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        scheduler.preferences.registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    override fun onStop() {
        scheduler.preferences.unregisterOnSharedPreferenceChangeListener(listener)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_destination", pendingDestination?.name)
        outState.putString("pending_frequency", pendingFrequency?.value)
        super.onSaveInstanceState(outState)
    }

    private fun configure(destination: BackupDestination, frequency: BackupFrequency) {
        if (frequency == BackupFrequency.OFF) {
            scheduler.setFrequency(destination, frequency)
        } else if (destination == BackupDestination.DRIVE && scheduler.preferences.getString("drive_account", null) == null) {
            toast(R.string.backup_auto_connect_first)
        } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            pendingDestination = destination
            pendingFrequency = frequency
            permission.launch(Manifest.permission.READ_SMS)
        } else if (destination == BackupDestination.LOCAL && !hasFolder()) {
            pendingDestination = destination
            pendingFrequency = frequency
            chooseFolder()
        } else {
            scheduler.setFrequency(destination, frequency)
            pendingDestination = null
            pendingFrequency = null
        }
        refresh()
    }

    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            scheduler.preferences.getString("auto_local_folder", null)?.let {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it))
            }
        }
        folder.launch(intent)
    }

    private fun hasFolder(): Boolean {
        val folder = scheduler.preferences.getString("auto_local_folder", null) ?: return false
        return requireContext().contentResolver.persistedUriPermissions.any {
            it.uri.toString() == folder && it.isReadPermission && it.isWritePermission
        }
    }

    private fun toast(resource: Int) = Toast.makeText(requireContext(), resource, Toast.LENGTH_LONG).show()

    private fun refresh() {
        if (!isAdded || preferenceScreen == null) return
        val prefs = scheduler.preferences
        for (destination in BackupDestination.values()) {
            val frequency = scheduler.frequency(destination)
            findPreference<ListPreference>(AutomaticBackupScheduler.key(destination, "frequency"))!!.value = frequency.value
            val time = prefs.getLong(AutomaticBackupScheduler.key(destination, "next"), 0)
            findPreference<Preference>(AutomaticBackupScheduler.key(destination, "next"))!!.summary =
                if (frequency == BackupFrequency.OFF || time == 0L) getString(R.string.backup_auto_off)
                else DateFormat.getMediumDateFormat(requireContext()).format(Date(time)) + " " +
                    DateFormat.getTimeFormat(requireContext()).format(Date(time))
            val storedStatus = prefs.getString(AutomaticBackupScheduler.key(destination, "status"), getString(R.string.backup_never))
            findPreference<Preference>(AutomaticBackupScheduler.key(destination, "status"))!!.summary =
                if (storedStatus == getString(R.string.backup_working) && runningWork[destination] != true)
                    getString(if (frequency == BackupFrequency.OFF) R.string.backup_auto_off else R.string.backup_auto_waiting)
                else storedStatus
            val ready = if (destination == BackupDestination.LOCAL) hasFolder() else prefs.getString("drive_account", null) != null
            findPreference<Preference>(AutomaticBackupScheduler.key(destination, "now"))!!.isEnabled =
                frequency != BackupFrequency.OFF && ready && activeWork[AutomaticBackupScheduler.workName(destination) + "_now"] != true
        }
        findPreference<Preference>("auto_local_folder")!!.summary = prefs.getString("auto_local_folder", null)?.let {
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfter(':') }.getOrDefault("")
        } ?: getString(R.string.backup_auto_choose_folder)
        findPreference<SwitchPreferenceCompat>("auto_drive_unmetered")!!.isChecked = prefs.getBoolean("auto_drive_unmetered", true)
    }
}
