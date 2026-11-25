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
val Spend = Color(0xFFC2410C)          // debits: orange
val Credit = Color(0xFF15803D)         // credits: green
val NoteBg = Color(0xFFEEF3FD)
val NoteFg = Color(0xFF123F8F)
val Brand = Color(0xFF3F51E8)
val BrandDark = Color(0xFF2F3FC4)
val BrandLight = Color(0xFFE6E9FF)
val DebitTile = Color(0xFFFFF5EE)
val DebitTileBorder = Color(0xFFF8D9C4)
val CreditTile = Color(0xFFEFF8F2)
val CreditTileBorder = Color(0xFFCBE8D4)
val DebitPage = Color(0xFFFDF1E8)
val CreditPage = Color(0xFFEAF6EE)

val CATEGORY_COLORS = mapOf(
    "Food & dining" to 0xFFC2410C, "Groceries" to 0xFF3F7D20, "Shopping" to 0xFF7C3AED, "Travel" to 0xFF0369A1,
    "Transport" to 0xFF475569, "Fuel & vehicle" to 0xFF9A3412, "Health" to 0xFFBE123C, "Personal care" to 0xFFBE185D,
    "Subscriptions & apps" to 0xFFA21CAF, "Entertainment" to 0xFFA16207, "Insurance" to 0xFF0E7490,
    "Investments" to 0xFF1E3A8A, "UPI to people" to 0xFF0F766E, "Bank charges" to 0xFF57534E,
    "Own account (likely)" to 0xFF4F46E5, "Other" to 0xFF6B7280, "Salary" to 0xFF15803D, "Refund" to 0xFF0E7490,
    "Reversal" to 0xFF0369A1, "Meal allowance" to 0xFF3F7D20, "From people (UPI)" to 0xFF0F766E,
    "Bank transfer in" to 0xFF1E3A8A, "Dividend" to 0xFFA16207, "Other credit" to 0xFF6B7280,
).mapValues { Color(it.value) }

fun categoryColor(c: String): Color = CATEGORY_COLORS[c] ?: Color(0xFF6B7280)
