package com.smsledger.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToLong

/** ₹ in Indian digit grouping: 1234567.5 -> ₹12,34,567.50 */
fun inr(v: Double, paise: Boolean = true): String {
    val neg = v < 0
    val a = abs(v)
    var whole = a.toLong()
    var frac = ((a - whole) * 100).roundToLong()
    if (frac == 100L) { whole += 1; frac = 0 }
    if (!paise && frac >= 50) whole += 1
    val s = whole.toString()
    val grouped = if (s.length <= 3) s else {
        val head = s.dropLast(3)
        val parts = head.reversed().chunked(2).map { it.reversed() }.reversed()
        parts.joinToString(",") + "," + s.takeLast(3)
    }
    val tail = if (paise && frac > 0) ".%02d".format(frac) else ""
    return (if (neg) "−" else "") + "₹" + grouped + tail
}

private val NICE = mapOf(
    "WWWBIGBA5698757" to "BigBasket", "ASSPL" to "Amazon", "Google Asi" to "Google", "ETERNAL LIM" to "Zomato / Blinkit",
    "GLOBAL SIM* SUPERALINK" to "Global SIM", "IMPS Charg" to "IMPS charge", "SETU" to "Setu (₹1 account check)",
    "PAYZAPP7610680" to "PayZapp", "Credit Card payment" to "HDFC credit card bill", "SBI Cards" to "SBI credit card bill",
    "Nykaa E Retail Pvt" to "Nykaa", "CHRGS- SMS" to "SMS charges",
)

/** Friendlier display name for a payee / payer. */
fun displayName(party: String): String {
    Regex("^(RTGS|NEFT)-([A-Z]{4})-").find(party)?.let { return "${it.groupValues[1]} from ${it.groupValues[2]}" }
    var p = party.replace(" CHENNAI", "").trim()
    NICE[p]?.let { return it }
    if ('@' in p) p = p.substringBefore('-').substringBefore('@')
    if (p.startsWith("Swiggy", ignoreCase = true)) return "Swiggy"
    val allSameCase = p == p.uppercase() || p == p.lowercase()
    return if (allSameCase) p.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else p
}

val Ground = Color(0xFFF4F5F7)
val Ink = Color(0xFF14171F)
val Muted = Color(0xFF5B6270)
val Line = Color(0xFFE3E6EB)
val Spend = Color(0xFFB8430F)
val Credit = Color(0xFF1D5FD1)
val NoteBg = Color(0xFFEEF3FD)
val NoteFg = Color(0xFF123F8F)

val CATEGORY_COLORS = mapOf(
    "Shopping" to Color(0xFF6B3FD4), "Groceries" to Color(0xFF3F6B22), "UPI to people" to Color(0xFF0F766E),
    "Food & dining" to Color(0xFFB8430F), "Travel" to Color(0xFF1D5FD1), "Investments" to Color(0xFF14171F),
    "Own account (likely)" to Color(0xFF7A5AF8), "Health" to Color(0xFFB42318), "Subscriptions & apps" to Color(0xFFA3195B),
    "Entertainment" to Color(0xFF8A5A00), "Transport" to Color(0xFF3A404C), "Bank charges" to Color(0xFF5B6270),
    "Insurance" to Color(0xFF0E7490), "Fuel & vehicle" to Color(0xFF9A3412), "Personal care" to Color(0xFFBE185D),
)

fun categoryColor(c: String): Color = CATEGORY_COLORS[c] ?: Color(0xFF8B919C)
