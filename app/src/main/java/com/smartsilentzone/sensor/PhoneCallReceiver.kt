package com.smartsilentzone.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PhoneCallReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsManager: BreakGlassSmsManager

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var incomingNumber: String? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            number?.let { incomingNumber = it }

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    lastState = TelephonyManager.CALL_STATE_RINGING
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // If it was ringing and now idle without off-hook, it's a missed call
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        incomingNumber?.let { smsManager.handleMissedCall(it) }
                    }
                    lastState = TelephonyManager.CALL_STATE_IDLE
                    incomingNumber = null
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    lastState = TelephonyManager.CALL_STATE_OFFHOOK
                }
            }
        }
    }
}
