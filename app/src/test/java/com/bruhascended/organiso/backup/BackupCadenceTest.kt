package com.bruhascended.organiso.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class BackupCadenceTest {
    @Test fun monthlyReturnsToTheOriginalDayAfterShortMonths() {
        val january = ZonedDateTime.parse("2027-01-31T02:30:00+05:30[Asia/Kolkata]")
        val february = nextBackupTime(BackupFrequency.MONTHLY, january, 31, LocalTime.of(2, 30))
        val march = nextBackupTime(BackupFrequency.MONTHLY, february, 31, LocalTime.of(2, 30))
        assertEquals(28, february.dayOfMonth)
        assertEquals(31, march.dayOfMonth)
        assertEquals(3, march.monthValue)
    }

    @Test fun monthlyUsesLeapDaysAndCrossesYears() {
        val january = ZonedDateTime.parse("2028-01-31T12:00:00Z[UTC]")
        assertEquals(29, nextBackupTime(BackupFrequency.MONTHLY, january, 31, LocalTime.NOON).dayOfMonth)
        val december = ZonedDateTime.parse("2028-12-31T12:00:00Z[UTC]")
        assertEquals(2029, nextBackupTime(BackupFrequency.MONTHLY, december, 31, LocalTime.NOON).year)
    }

    @Test fun dailyReturnsToThePreferredTimeAfterADaylightSavingGap() {
        val start = ZonedDateTime.parse("2027-03-13T02:30:00-05:00[America/New_York]")
        val gap = nextBackupTime(BackupFrequency.DAILY, start, 13, LocalTime.of(2, 30))
        val next = nextBackupTime(BackupFrequency.DAILY, gap, 13, LocalTime.of(2, 30))
        assertEquals(3, gap.hour)
        assertEquals(2, next.hour)
        assertEquals(30, next.minute)
        assertTrue(gap.isAfter(start))
    }

    @Test fun weeklyUsesSevenCalendarDaysAcrossDaylightSaving() {
        val start = ZonedDateTime.parse("2027-03-07T10:15:00-05:00[America/New_York]")
        val next = nextBackupTime(BackupFrequency.WEEKLY, start, 7, LocalTime.of(10, 15))
        assertEquals(start.dayOfWeek, next.dayOfWeek)
        assertEquals(14, next.dayOfMonth)
        assertEquals(10, next.hour)
        assertEquals(15, next.minute)
    }
}
