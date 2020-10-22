package com.bruhascended.organiso.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import com.bruhascended.core.constants.LABEL_PERSONAL
import com.bruhascended.core.constants.LABEL_TRANSACTIONS
import com.bruhascended.core.constants.MESSAGE_TYPE_INBOX
import com.bruhascended.core.db.MainDaoProvider
import com.bruhascended.core.db.MessageDbFactory
import com.bruhascended.core.model.getOtp
import com.bruhascended.organiso.MainActivity
import com.bruhascended.organiso.R

class OtpDeleteService: Service() {

    override fun onCreate() {
        super.onCreate()
        val mContext = this
        val mainDaos = MainDaoProvider(mContext).getMainDaos()
        val pendingIntent: PendingIntent =
            Intent(mContext, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(mContext, 0, notificationIntent, 0)
            }

        val notification: Notification = Notification.Builder(mContext, LABEL_PERSONAL.toString())
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Deleting existing OTPs")
            .setSmallIcon(R.drawable.message)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, true)
            .build()

        mContext.startForeground(10123123, notification)

        val otpMessages = hashSetOf<String>()
        for (con in mainDaos[LABEL_TRANSACTIONS].loadAllSync()) {
            MessageDbFactory(mContext).of(con.clean).apply {
                manager().loadAllSync().forEach {
                    if (getOtp(it.text) != null && it.type == MESSAGE_TYPE_INBOX &&
                        System.currentTimeMillis()-it.time > 15*60*1000) {
                        manager().deleteFromInternal(it)
                        otpMessages.add(it.text)
                    }
                }
                val it = manager().loadLastSync()
                if (it == null) {
                    mainDaos[2].delete(con)
                } else {
                    if (con.lastSMS != it.text ||
                        con.time != it.time ||
                        con.lastMMS != (it.path != null)
                    ) {
                        con.lastSMS = it.text
                        con.time = it.time
                        con.lastMMS = it.path != null
                        mainDaos[2].update(con)
                    }
                }
                close()
            }
        }
        deleteMessages(otpMessages)
        stopForeground(true)
    }

    private fun deleteMessages(messages: HashSet<String>) {
        try {
            val uriSms: Uri = Uri.parse("content://sms/")
            val c: Cursor? = contentResolver.query(
                uriSms, arrayOf(
                    "_id", "body"
                ), null, null, null
            )
            if (c != null && c.moveToFirst()) {
                do {
                    val id: Long = c.getLong(0)
                    val body: String = c.getString(1)
                    if (body in messages) {
                        contentResolver.delete(
                            Uri.parse("content://sms/$id"), null, null
                        )
                    }
                } while (c.moveToNext())
            }
            c?.close()
        } catch (e: Exception) { }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}