package com.bruhascended.sms.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bruhascended.sms.ConversationActivity
import com.bruhascended.sms.MainActivity
import com.bruhascended.sms.R
import com.bruhascended.sms.StartActivity
import com.bruhascended.sms.data.SMSManager
import com.bruhascended.sms.data.labelText


class SMSReceiver : BroadcastReceiver() {
    private var count = 0
    private val descriptionText = arrayOf(
        R.string.text_1,
        R.string.text_2,
        R.string.text_3,
        R.string.text_4,
        R.string.text_5
    )

    private val importance = arrayOf(
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_MAX,
        NotificationManager.IMPORTANCE_LOW,
        NotificationManager.IMPORTANCE_NONE
    )


     private lateinit var mContext: Context

     private fun createNotificationChannel() {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             val notificationManager: NotificationManager =
                 mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

             if (notificationManager.notificationChannels.isEmpty()) {
                 for (i in 0..4) {
                     val name = mContext.getString(labelText[i])
                     val descriptionText = mContext.getString(descriptionText[i])
                     val channel = NotificationChannel(i.toString(), name, importance[i]).apply {
                         description = descriptionText
                     }
                     notificationManager.createNotificationChannel(channel)
                 }
             }
         }
     }

    override fun onReceive(context: Context, intent: Intent) {
        mContext = context
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pduObjects = bundle["pdus"] as Array<*>
                Thread( Runnable {
                    val smm = SMSManager(context)

                    for (aObject in pduObjects) {
                        createNotificationChannel()
                        val currentSMS = SmsMessage.createFromPdu(aObject as ByteArray, bundle.getString("format"))
                        val sender = currentSMS.displayOriginatingAddress.toString()
                        val content = currentSMS.messageBody.toString()
                        smm.putMessage(sender, content)
                    }

                    smm.getLabels()
                    val messages = smm.saveMessages()

                    for (pair in messages) {
                        val message = pair.first
                        val conversation = pair.second
                        val yeah = Intent(context, ConversationActivity::class.java)
                            .putExtra("ye", conversation)
                        val pendingIntent: PendingIntent = PendingIntent.getActivity(
                            context, 0, yeah, PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        if (conversation.label != 5) {
                            val builder = NotificationCompat.Builder(context, conversation.label.toString())
                                    .setSmallIcon(R.drawable.ic_launcher_round)
                                    .setContentTitle(conversation.name?: message.sender)
                                    .setContentText(message.text)
                                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setContentIntent(pendingIntent)
                                    .setAutoCancel(true)
                            with(NotificationManagerCompat.from(context)) {
                                notify(count++, builder.build())
                            }
                        }
                    }
                }).start()
            }
        }
    }
}
