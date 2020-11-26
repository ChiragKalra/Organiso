package com.bruhascended.organiso.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.bruhascended.core.constants.LABEL_PERSONAL
import com.bruhascended.core.data.MainDaoProvider
import com.bruhascended.organiso.MainActivity
import com.bruhascended.organiso.R

class PersonalMoveService: Service() {

    override fun onCreate() {
        super.onCreate()
        val mContext = this
        val mainDaos = MainDaoProvider(mContext).getMainDaos()
        val pendingIntent: PendingIntent =
            Intent(mContext, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(mContext, 0, notificationIntent, 0)
            }

        val notification: Notification =
            Notification.Builder(mContext, LABEL_PERSONAL.toString())
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.scan_personal_contacts))
                .setSmallIcon(R.drawable.message)
                .setContentIntent(pendingIntent)
                .setProgress(0, 0, true)
                .build()

        mContext.startForeground(10123123, notification)
        Thread {
            for (con in mainDaos[LABEL_PERSONAL].loadAllSync()) {
                var label: Int
                con.probabilities.clone().apply {
                    this[LABEL_PERSONAL] = 0f
                    label = toList().indexOf(maxOrNull())
                }
                mainDaos[LABEL_PERSONAL].delete(con)
                con.label = label
                mainDaos[label].insert(con)
            }
            stopForeground(true)
            stopSelf()
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}