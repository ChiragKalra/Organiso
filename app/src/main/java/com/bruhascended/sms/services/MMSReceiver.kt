package com.bruhascended.sms.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class MMSReceiver : BroadcastReceiver() {


     private lateinit var mContext: Context

    override fun onReceive(context: Context, intent: Intent) {
        mContext = context
        if (intent.action == "android.provider.Telephony.MMS_RECEIVED") {
            /*
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
             */
        }
    }
}
