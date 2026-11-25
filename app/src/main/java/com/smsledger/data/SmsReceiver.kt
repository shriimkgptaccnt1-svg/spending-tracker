package com.smsledger.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.smsledger.parser.Sms
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Processes each new SMS as it arrives, so the app is up to date without opening it. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts[0].displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.messageBody ?: "" }
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(parts[0].timestampMillis), ZoneId.systemDefault())
        val pending = goAsync()
        Thread {
            try {
                SmsScanner.scan(context.applicationContext, listOf(Sms(sender, time, body)))
            } finally {
                pending.finish()
            }
        }.start()
    }
}
