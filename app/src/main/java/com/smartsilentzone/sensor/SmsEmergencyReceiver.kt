package com.smartsilentzone.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SmsEmergencyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsManager: BreakGlassSmsManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (sms in messages) {
                val sender = sms.displayOriginatingAddress ?: sms.originatingAddress
                val body = sms.displayMessageBody ?: sms.messageBody
                if (sender != null && body != null) {
                    smsManager.handleIncomingSms(sender, body)
                }
            }
        }
    }
}
