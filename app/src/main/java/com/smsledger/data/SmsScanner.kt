package com.smsledger.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.smsledger.parser.Owner
import com.smsledger.parser.START_DATE
import com.smsledger.parser.Sms
import com.smsledger.parser.parse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Reads bank SMS from the phone's inbox (from 1 Aug 2026), turns them into transactions and
 * saves the transaction details. The inbox itself is never changed and no SMS text is stored.
 */
object SmsScanner {
    private val lock = Any()

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** @param extra messages that just arrived and may not be in the inbox yet. */
    fun scan(context: Context, extra: List<Sms> = emptyList()): Int = synchronized(lock) {
        if (!hasPermission(context)) return 0
        val zone = ZoneId.systemDefault()
        val since = START_DATE.atZone(zone).toInstant().toEpochMilli()
        val messages = mutableListOf<Sms>()
        context.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "date", "body"),
            "date >= ?", arrayOf(since.toString()), "date ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val sender = c.getString(0) ?: continue
                val body = c.getString(2) ?: continue
                val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(1)), zone)
                messages += Sms(sender, time, body)
            }
        }
        messages += extra
        val store = Store(context)
        val owner = Owner(listOfNotNull(store.meta("owner_name")))
        val result = parse(messages, owner)
        store.upsert(result.txns)
        store.setMeta("last_scan", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toString())
        store.close()
        result.txns.size
    }
}
