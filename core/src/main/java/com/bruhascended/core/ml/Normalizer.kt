package com.bruhascended.core.ml

import java.util.*
import kotlin.math.abs

/*
                    Copyright 2020 Chirag Kalra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

fun Boolean.toFloat() = if (this) 1f else 0f

// removes all instances of regex from text
private fun removeRegex (text: String, regex: Regex) : Pair<String, Float> {
    val many = regex.findAll(text)
    var newText = text
    for (one in many)
        newText = newText.replace(one.groupValues.first().toString(), " ")
    return newText.trim() to (!many.none()).toFloat()
}

fun removeDecimals (message: String): Pair<String, Float> {
    // identifies decimals, time, date and big numbers separated by ','
    val decimal = Regex("\\d*[.:,/\\\\]+\\d+")
    return removeRegex(message, decimal)
}

// removes dates from sms text
fun removeDates (message: String): Pair<String, Float> {
    val date = Regex("(?:\\d{1,2}[-/th|st|nd|rd\\s]*)?" +
            "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)?[a-z\\s,.]*" +
            "(?:\\d{1,2}[-/th|st|nd|rd)\\s,]*)+(?:\\d{2,4})+")
    return removeRegex(message, date)
}

// removes large numbers from sms text
fun removeNumbers (message: String): Pair<String, Float> {
    val number = Regex("(?<!\\d)\\d{4,25}(?!\\d)")
    return removeRegex(message, number)
}

// removes lines
fun removeLines(message: String): String {
    return message.replace('\n', ' ').replace('\r', ' ')
}

// trims all urls in sms text down to their domain names
fun trimUrls(message: String): Pair<String, Float> {
    val urlRe = Regex("(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\." +
            "[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/" +
            "(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})")
    val urls = urlRe.findAll(message)
    var newMessage = message
    for (url in urls) {
        val trimmedUrl =
            url.toString().split("//").last().split("/")[0].split('?')[0]
                .replace("www.", "").split('.')[0]
        newMessage = newMessage.replace(url.toString(), trimmedUrl)
    }
    return newMessage.trim() to (!urls.none()).toFloat()
}

// stem words to root meaning
fun stem(message: String): String {
    var newMessage = Regex("[\\-.']+").replace(message, "")  // no space
    newMessage = Regex("[/,:;_!<>()&^*#@+]+").replace(newMessage, " ") // added space
    return  newMessage.toLowerCase(Locale.ROOT)
}

// changes long time to [0,1] ([day, night])
fun time (date: Long): Float {
    val cl = Calendar.getInstance()
    cl.timeInMillis = date
    val seriesTime = cl.get(Calendar.SECOND) + cl.get(Calendar.MINUTE)*60
    return abs(abs(seriesTime-240) -(60*12)) / 720f
}

// returns null if message has no otp otherwise returns the 4-6 digit OTP
fun getOtp(message: String): String? {
    val sepRegex = Regex("(?<=\\d)[\\s\\-](?=\\d)")
    val content = message.toLowerCase(Locale.ROOT).replace(sepRegex, "")
    val otpRegex = Regex("\\b\\d{4,6}\\b")
    val otps = otpRegex.findAll(content).toList()

    if (otps.isEmpty()) return null
    val otp = otps.first().groups.first()!!.value
    content.apply {
        if (contains("otp") || contains("code") || contains("key") ||
            contains("pin") || contains("one time password") || contains("verify") ||
            (contains("number") && (contains("registration") || contains("verification")))
        )
            return otp
    }
    return null
}