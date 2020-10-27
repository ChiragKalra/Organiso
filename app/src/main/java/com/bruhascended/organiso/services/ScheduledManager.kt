package com.bruhascended.organiso.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.*
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.MessageDao
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.db.ScheduledMessage
import java.util.concurrent.TimeUnit
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

class ScheduledManager(
    private val mContext: Context,
    private val mDao: MessageDao
) {
    private val mWorkManager = WorkManager.getInstance(mContext)

    class SendWork(
        private val mContext: Context,
        workerParams: WorkerParameters
    ): Worker(mContext, workerParams) {

        override fun doWork(): Result {
            val number = inputData.getString(EXTRA_NUMBER)!!
            val text = inputData.getString(EXTRA_MESSAGE_TEXT)!!
            val file = inputData.getString(EXTRA_FILE_PATH)
            val time = inputData.getLong(EXTRA_TIME, 0)


            mContext.startService(
                Intent(mContext, SenderService::class.java).apply {
                    putExtra(EXTRA_NUMBER, number)
                    putExtra(EXTRA_MESSAGE_TEXT, text)
                    data = Uri.parse(file)
                }
            )

            MessageDbFactory(mContext).of(number).apply {
                manager().deleteScheduled(manager().findScheduledByTime(time))
            }

            return Result.success()
        }
    }

    fun add(scheduledTime: Long, number: String, text: String, data: Uri?) {
        val date = System.currentTimeMillis()
        val path = mContext.saveFile(data, date.toString())
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(SendWork::class.java)
            .setConstraints(constraints)
            .setInitialDelay(abs(scheduledTime - date), TimeUnit.MILLISECONDS)
            .addTag(date.toString())
            .setInputData(
                Data.Builder()
                    .putString(EXTRA_NUMBER, number)
                    .putString(EXTRA_MESSAGE_TEXT, text)
                    .putString(EXTRA_FILE_PATH, path)
                    .putLong(EXTRA_TIME, scheduledTime)
                    .build()
            ).build()
        mWorkManager.enqueue(request)
        mDao.insertScheduled(
            ScheduledMessage(
                date,
                text,
                scheduledTime,
                number,
                path,
            )
        )
    }

    fun remove(message: ScheduledMessage) {
        mWorkManager.cancelAllWorkByTag(message.id.toString())
        mDao.deleteScheduled(message)
    }
}