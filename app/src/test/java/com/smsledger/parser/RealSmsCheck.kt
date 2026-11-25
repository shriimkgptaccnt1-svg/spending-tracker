package com.smsledger.parser

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Optional check against a real SMS export kept on the developer's machine (never committed).
 * SMS_FIXTURE: a file with one SMS per line: sender<TAB>ISO time<TAB>body.
 * SMS_OWNER: the owner's name. Writes the parsed rows to SMS_FIXTURE_OUT as tab-separated lines.
 */
class RealSmsCheck {
    @Test fun parseRealExport() {
        val fixture = System.getenv("SMS_FIXTURE")
        assumeTrue("set SMS_FIXTURE to run", fixture != null && File(fixture).exists())
        val messages = File(fixture!!).readLines().filter { it.isNotBlank() }.map { line ->
            val (sender, time, body) = line.split("\t", limit = 3)
            Sms(sender, LocalDateTime.parse(time), body)
        }
        val result = parse(messages, Owner(listOfNotNull(System.getenv("SMS_OWNER"))))
        File(System.getenv("SMS_FIXTURE_OUT") ?: "$fixture.out").printWriter().use { w ->
            for (t in result.txns) {
                w.println(listOf(t.time.toLocalDate(), t.amount, t.direction, t.bank, t.account, t.party, t.kind, t.category, t.smsCount).joinToString("\t"))
            }
        }
    }
}
