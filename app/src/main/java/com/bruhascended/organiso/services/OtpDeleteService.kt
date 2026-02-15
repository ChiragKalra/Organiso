package com.bruhascended.organiso.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.bruhascended.core.constants.LABEL_PERSONAL
import com.bruhascended.core.constants.LABEL_TRANSACTIONS
import com.bruhascended.core.constants.MESSAGE_TYPE_INBOX
import com.bruhascended.core.constants.deleteSMS
import com.bruhascended.core.data.MainDaoProvider
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
                PendingIntent.getActivity(mContext, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val notification: Notification = Notification.Builder(mContext, LABEL_PERSONAL.toString())
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.deleting_otp))
            .setSmallIcon(R.drawable.message)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, true)
            .build()

        mContext.startForeground(10123123, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        Thread {
            for (con in mainDaos[LABEL_TRANSACTIONS].loadAllSync()) {
                MessageDbFactory(mContext).of(con.number).apply {
                    manager().loadAllSync().forEach {
                        if (getOtp(it.text) != null && it.type == MESSAGE_TYPE_INBOX &&
                            System.currentTimeMillis() - it.time > 15 * 60 * 1000
                        ) {
                            manager().deleteFromInternal(it)
                            mContext.deleteSMS(it.id!!)
                        }
                    }
                    val lastMsg = manager().loadLastSync()
                    if (lastMsg == null) {
                        mainDaos[LABEL_TRANSACTIONS].delete(con)
                    } else {
                        if (con.time != lastMsg.time) {
                            con.time = lastMsg.time
                            mainDaos[LABEL_TRANSACTIONS].insert(con)
                        }
                    }
                    close()
                }
            }
            stopForeground(true)
            stopSelf()
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}