package com.bruhascended.organiso.backup

import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime

enum class BackupFrequency(val value: String) {
    OFF("off"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

    companion object {
        fun from(value: String?) = values().firstOrNull { it.value == value } ?: OFF
    }
}

/** Retain the chosen day/time across short months and daylight-saving transitions. */
fun nextBackupTime(
    frequency: BackupFrequency,
    previous: ZonedDateTime,
    anchorDay: Int,
    anchorTime: LocalTime
): ZonedDateTime {
    val date = when (frequency) {
        BackupFrequency.DAILY -> previous.toLocalDate().plusDays(1)
        BackupFrequency.WEEKLY -> previous.toLocalDate().plusWeeks(1)
        BackupFrequency.MONTHLY -> YearMonth.from(previous).plusMonths(1).let {
            it.atDay(anchorDay.coerceIn(1, it.lengthOfMonth()))
        }
        BackupFrequency.OFF -> throw IllegalArgumentException("Disabled schedules have no next backup")
    }
    return date.atTime(anchorTime).atZone(previous.zone)
}
