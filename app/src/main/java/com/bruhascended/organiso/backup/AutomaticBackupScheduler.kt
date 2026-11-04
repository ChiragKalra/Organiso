package com.bruhascended.organiso.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executor

enum class BackupDestination(val key: String) { LOCAL("local"), DRIVE("drive") }

/** Each destination has its own cadence, pending work and cancellation generation. */
class AutomaticBackupScheduler(context: Context) {
    private val app = context.applicationContext
    val preferences = app.getSharedPreferences("sms_backup", 0)
    private val manager = WorkManager.getInstance(app)

    fun frequency(destination: BackupDestination) =
        BackupFrequency.from(preferences.getString(key(destination, "frequency"), "off"))

    fun setFrequency(destination: BackupDestination, frequency: BackupFrequency) = synchronized(lock) {
        val now = ZonedDateTime.now().withSecond(0).withNano(0)
        preferences.edit()
            .putString(key(destination, "frequency"), frequency.value)
            .putString(key(destination, "generation"), UUID.randomUUID().toString())
            .putInt(key(destination, "anchor_day"), now.dayOfMonth)
            .putInt(key(destination, "anchor_minute"), now.hour * 60 + now.minute)
            .remove(key(destination, "next"))
            .putBoolean(key(destination, "running"), false)
            .commit()
        val generation = generation(destination)
        val cancellation = manager.cancelAllWorkByTag(workName(destination))
        pendingCancellations[destination] = cancellation
        if (frequency == BackupFrequency.OFF) {
            return@synchronized
        } else {
            val next = nextBackupTime(frequency, now, now.dayOfMonth, now.toLocalTime()).toInstant().toEpochMilli()
            afterCancellation(destination, generation) { enqueue(destination, generation, next) }
        }
    }

    /** Updating a folder/network setting preserves the cadence already selected by the user. */
    fun refresh(destination: BackupDestination) = synchronized(lock) {
        if (frequency(destination) == BackupFrequency.OFF) return@synchronized
        val next = preferences.getLong(key(destination, "next"), System.currentTimeMillis())
        val generation = UUID.randomUUID().toString()
        preferences.edit().putString(key(destination, "generation"), generation).commit()
        pendingCancellations[destination] = manager.cancelAllWorkByTag(workName(destination))
        afterCancellation(destination, generation) { enqueue(destination, generation, next) }
    }

    fun runNow(destination: BackupDestination): UUID = synchronized(lock) {
        require(frequency(destination) != BackupFrequency.OFF)
        val request = request(destination, generation(destination), System.currentTimeMillis(), scheduled = false)
        val generation = generation(destination)
        afterCancellation(destination, generation) {
            manager.enqueueUniqueWork(workName(destination) + "_now", ExistingWorkPolicy.KEEP, request)
        }
        request.id
    }

    fun isCurrent(destination: BackupDestination, generation: String) =
        frequency(destination) != BackupFrequency.OFF && generation(destination) == generation

    fun generation(destination: BackupDestination): String =
        preferences.getString(key(destination, "generation"), "") ?: ""

    fun scheduleNext(destination: BackupDestination, generation: String, previous: Long) = synchronized(lock) {
        if (!isCurrent(destination, generation)) return@synchronized
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val minute = preferences.getInt(key(destination, "anchor_minute"), now.hour * 60 + now.minute)
        val anchorTime = LocalTime.of(minute / 60, minute % 60)
        val anchorDay = preferences.getInt(key(destination, "anchor_day"), now.dayOfMonth)
        var next = Instant.ofEpochMilli(previous).atZone(zone)
        do {
            next = nextBackupTime(frequency(destination), next, anchorDay, anchorTime)
        } while (!next.isAfter(now))
        enqueue(destination, generation, next.toInstant().toEpochMilli())
    }

    private fun enqueue(destination: BackupDestination, generation: String, at: Long) {
        preferences.edit().putLong(key(destination, "next"), at).commit()
        // A process restart/retry must never enqueue the same future occurrence twice.
        manager.enqueueUniqueWork(workName(destination) + "_${generation}_$at", ExistingWorkPolicy.KEEP,
            request(destination, generation, at, scheduled = true))
    }

    private fun afterCancellation(destination: BackupDestination, generation: String, block: () -> Unit) {
        val ready = Runnable {
            synchronized(lock) {
                if (isCurrent(destination, generation)) block()
            }
        }
        pendingCancellations[destination]?.result?.addListener(ready, directExecutor) ?: ready.run()
    }

    private fun request(destination: BackupDestination, generation: String, at: Long, scheduled: Boolean): OneTimeWorkRequest {
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true)
        if (destination == BackupDestination.DRIVE) constraints.setRequiredNetworkType(
            if (preferences.getBoolean("auto_drive_unmetered", true)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        return OneTimeWorkRequest.Builder(AutomaticBackupWorker::class.java)
            .setInputData(Data.Builder().putString("destination", destination.name).putString("generation", generation)
                .putLong("scheduled_at", at).putBoolean("scheduled", scheduled).build())
            .setConstraints(constraints.build())
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("automatic_sms_backup")
            .addTag(workName(destination))
            .build()
    }

    companion object {
        private val lock = Any()
        private val directExecutor = Executor { it.run() }
        private val pendingCancellations = HashMap<BackupDestination, Operation>()
        fun key(destination: BackupDestination, setting: String) = "auto_${destination.key}_$setting"
        fun workName(destination: BackupDestination) = "automatic_sms_backup_${destination.key}"
    }
}
