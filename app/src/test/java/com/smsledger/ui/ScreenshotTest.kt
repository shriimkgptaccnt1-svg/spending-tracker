package com.smsledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.smsledger.data.Row
import com.smsledger.parser.Owner
import com.smsledger.parser.Sms
import com.smsledger.parser.parse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/**
 * Renders the four screens. Uses a local real-SMS export if SMS_FIXTURE is set (see RealSmsCheck),
 * otherwise a few made-up SMS. Images land in app/src/test/snapshots (git-ignored).
 */
class ScreenshotTest {
    /** Saves full-size images (Paparazzi's default shrinks them), cropped to the content. */
    private class FullSize : SnapshotHandler {
        override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int) = object : SnapshotHandler.FrameHandler {
            override fun handle(image: BufferedImage) {
                var bottom = image.height - 1
                val bg = image.getRGB(2, image.height - 2)
                while (bottom > 0 && (0 until image.width step 7).all { image.getRGB(it, bottom) == bg }) bottom--
                val out = File("build/screens").apply { mkdirs() }
                ImageIO.write(image.getSubimage(0, 0, image.width, bottom + 1), "png", File(out, "${snapshot.testName.methodName}.png"))
            }
            override fun close() {}
        }
        override fun close() {}
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 12000, softButtons = false),
        snapshotHandler = FullSize(), maxPercentDifference = 100.0, useDeviceResolution = true,
    )

    private class FakeLedger(override val rows: List<Row>) : LedgerUi {
        override var year by mutableStateOf(2026)
        override var month by mutableStateOf(11)
        override val lastUpdated: LocalDateTime? = LocalDateTime.of(2026, 11, 25, 10, 46)
        override val scanning = false
    }

    private fun ledger(): FakeLedger {
        val fixture = System.getenv("SMS_FIXTURE")
        val messages = if (fixture != null && File(fixture).exists()) {
            File(fixture).readLines().filter { it.isNotBlank() }.map { l ->
                val (s, t, b) = l.split("\t", limit = 3); Sms(s, LocalDateTime.parse(t), b)
            }
        } else listOf(
            Sms("VM-IOBCHN-S", LocalDateTime.parse("2026-11-05T10:00:00"),
                "Your a/c XXXXX78 debited for payee SWIGGY for Rs. 250.00 on 2026-11-05, ref 612345678901.If not you, report to your bank immediately-IOB."),
            Sms("AD-HDFCBK-S", LocalDateTime.parse("2026-11-06T10:00:00"),
                "Spent Rs.1500 On HDFC Bank Card 1234 At AMAZON On 2026-11-06:10:00:00.Not You?"),
            Sms("VD-HDFCBK-S", LocalDateTime.parse("2026-11-07T09:00:00"),
                "Alert! Paid Rs. 20,000.00 For: Credit Card payment From HDFC Bank A/c XX4321 Via Online Banking."),
        )
        val txns = parse(messages, Owner(listOfNotNull(System.getenv("SMS_OWNER")))).txns
        return FakeLedger(txns.map {
            Row(it.id, it.time, it.amount, it.direction, it.account, it.bank, it.party, it.kind, it.category, it.smsCount)
        })
    }

    @Composable
    private fun Frame(tab: Int, content: @Composable () -> Unit) {
        MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Ground, surface = Ground)) {
            Column(Modifier.fillMaxSize().background(Ground)) {
                Box(Modifier.weight(1f, fill = false)) { content() }
                NavigationBar(containerColor = Color.White, modifier = Modifier.fillMaxWidth()) {
                    listOf("Overview" to Icons.Default.Home, "Over ₹10k" to Icons.Default.KeyboardArrowUp,
                        "Under ₹10k" to Icons.Default.List, "Year" to Icons.Default.DateRange).forEachIndexed { i, (l, ic) ->
                        NavigationBarItem(selected = tab == i, onClick = {}, icon = { Icon(ic, null) }, label = { Text(l, fontSize = 11.sp) })
                    }
                }
            }
        }
    }

    @Test fun overview() { val l = ledger(); paparazzi.snapshot { Frame(0) { OverviewScreen(l) {} } } }
    @Test fun over10k() { val l = ledger(); paparazzi.snapshot { Frame(1) { TxnListScreen(l, true, {}, {}) } } }
    @Test fun under10k() { val l = ledger(); paparazzi.snapshot { Frame(2) { TxnListScreen(l, false, {}, {}) } } }
    @Test fun year() { val l = ledger(); paparazzi.snapshot { Frame(3) { YearScreen(l) } } }
}
