package com.bruhascended.organiso.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.*

class DateTimeProvider(
    private val mContext: Context
) {

    fun getCondensed(time: Long): String {
        val smsTime = Calendar.getInstance().apply { timeInMillis = time }
        val now = Calendar.getInstance()

        return when {
            DateUtils.isToday(time) -> DateFormat.format(
                if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa",
                smsTime
            ).toString()

            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"

            now[Calendar.WEEK_OF_YEAR] == smsTime[Calendar.WEEK_OF_YEAR] &&
                    now[Calendar.YEAR] == smsTime[Calendar.YEAR] -> DateFormat.format(
                "EEEE", smsTime
            ).toString()

            now[Calendar.YEAR] == smsTime[Calendar.YEAR] -> DateFormat.format(
                "d MMMM", smsTime
            ).toString()

            else -> DateFormat.format("dd/MM/yyyy", smsTime).toString()
        }
    }


    fun getFull(time: Long): String {
        val smsTime = Calendar.getInstance().apply {
            timeInMillis = time
        }

        val now = Calendar.getInstance()

        val timeString = DateFormat.format(
            if (DateFormat.is24HourFormat(mContext)) "H:mm" else "h:mm aa",
            smsTime
        ).toString()
        return when {
            DateUtils.isToday(time) -> timeString
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "$timeString,\nYesterday"
            now[Calendar.YEAR] == smsTime[Calendar.YEAR] ->
                timeString + ",\n" + DateFormat.format("d MMMM", smsTime).toString()
            else ->
                timeString + ",\n" + DateFormat.format("dd/MM/yyyy", smsTime).toString()
        }
    }
}