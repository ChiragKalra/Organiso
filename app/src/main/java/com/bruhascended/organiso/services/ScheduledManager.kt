package com.bruhascended.organiso.services

import android.content.Context
import android.net.Uri
import androidx.work.*
import com.bruhascended.core.constants.*
import com.bruhascended.core.db.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ScheduledManager (
    private val mContext: Context,
    private val mDao: MessageDao
) {
    private val mWorkManager = WorkManager.getInstance(mContext)

    class SendWork(
        private val mContext: Context,
        workerParams: WorkerParameters
    ): Worker(mContext, workerParams) {

        override fun doWork(): Result {
            val conversation = inputData.getString(EXTRA_CONVERSATION_JSON).toConversation()
            val text = inputData.getString(EXTRA_MESSAGE_TEXT)!!
            val file = inputData.getString(EXTRA_FILE_PATH)
            val time = inputData.getLong(EXTRA_TIME,0)

            if (file == null) {
                SMSSender(mContext, arrayOf(conversation)).sendSMS(text)
            } else {
                MMSSender(mContext, arrayOf(conversation)).sendMMS(text, Uri.fromFile(File(file)))
            }

            MessageDbFactory(mContext).of(conversation.clean).apply {
                manager().deleteScheduled(manager().findScheduledByTime(time))
            }

            return Result.success()
        }
    }

    fun add(scheduledTime: Long, conversation: Conversation, text: String, data: Uri?) {
        val date = System.currentTimeMillis()
        val path = data?.saveFile(mContext, date.toString())
        val request = OneTimeWorkRequest.Builder(SendWork::class.java)
            .setInitialDelay(abs(scheduledTime - date), TimeUnit.MILLISECONDS)
            .addTag(date.toString())
            .setInputData(
                Data.Builder()
                    .putString(EXTRA_CONVERSATION_JSON, conversation.toString())
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
                conversation.address,
                path,
            )
        )
    }

    fun remove(message: ScheduledMessage) {
        mWorkManager.cancelAllWorkByTag(message.id.toString())
        mDao.deleteScheduled(message)
    }
}