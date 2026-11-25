package com.smsledger.parser

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * Turns bank SMS into transactions.
 *  - Only the bank / card / meal-wallet SMS formats in PATTERNS are read; OTPs, offers,
 *    notices and logins are ignored.
 *  - Only transactions on or after START_DATE are kept.
 *  - Credit card bill payments are listed but not counted in debits.
 *  - Reversed card payments are kept: the debit stays and the reversal is a credit.
 *  - One payment = one row: repeat SMS for the same payment are merged; an SMS already
 *    processed (same text) is skipped.
 *  - Nothing is auto-removed. Over ₹10,000 is shown separately; the user deletes rows.
 *  - Categories are filled in automatically and can be changed by the user.
 *  - No personal details live in this code: the user's accounts are detected from their own SMS,
 *    and their name (used only to hint "Own account") is entered in the app's Settings.
 */

val START_DATE: LocalDateTime = LocalDate.of(2026, 8, 1).atStartOfDay()
const val BIG = 10_000.0

data class Sms(val sender: String, val time: LocalDateTime, val body: String)

data class Txn(
    val time: LocalDateTime,
    val amount: Double,
    val direction: Direction,
    var account: String,
    val bank: String,
    /** Account digits as written in the SMS (may be 2-6 digits, or empty). */
    val accountDigits: String = "",
    /** "a/c", "card", "loan" or "meal card". */
    val accountType: String = "a/c",
    var party: String,
    var kind: Kind,
    var ref: String,
    val format: String,
    val fingerprint: String,
    /** SMS text, kept in memory during a parse only; never written to storage. */
    val text: String = "",
    val merged: MutableList<Txn> = mutableListOf(),
    var category: String = "",
) {
    val id: String get() = fingerprint
    val over10k: Boolean get() = amount > BIG
    val smsCount: Int get() = 1 + merged.size
    val counted: Boolean get() = kind != Kind.CARD_PAYMENT
}

enum class Direction { DEBIT, CREDIT }
enum class Kind { NORMAL, CARD_PAYMENT, REVERSAL, MANUAL }

data class Skipped(val sms: Sms, val reason: String)
data class ParseResult(val txns: List<Txn>, val skipped: List<Skipped>)

private val BANK_ENTITIES = mapOf(
    "HDFCBK" to "HDFC", "IOBCHN" to "IOB", "SBICRD" to "SBI Card", "MYSBIC" to "SBI Card",
    "CBSSBI" to "SBI", "SBYONO" to "SBI", "SBIPSG" to "SBI", "SBIUPI" to "SBI", "SBIINB" to "SBI", "SBIBNK" to "SBI",
    "INDUSB" to "IndusInd", "DCBANK" to "DCB", "PLUXEE" to "Pluxee",
)

private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private val SKIP = listOf(
    rx("""\bOTP\b|One Time Password""") to "OTP",
    rx("""will be deducted|Ignore if paid""") to "Upcoming autopay notice",
    rx("""E-statement|Total Amt Due|Min Amt Due""") to "Card statement",
    rx("""limit .* (revised|changed) to|txn locked|is active for""") to "Card settings",
    rx("""declined""") to "Declined attempt",
    rx("""RTGS transaction initiated""") to "Initiated (confirmed by a later SMS)",
    rx("""You have done a Transaction from your IOB internet banking""") to "Repeat of IMPS alert",
    rx("""convert your .* transaction .* EMI""") to "EMI offer",
    rx("""RECEIVED TOWARDS YOUR CREDIT CARD|was credited to your card ending|credited to your SBI Credit Card|for your SBI Credit Card has been successfully processed""")
        to "Card-side receipt of a bill payment",
)

private val CARD_PAYMENT = rx(
    """For: Credit Card payment|BillPay/Credit Card payment|payee SBI Cards|payee (CRED|HDFC CC|HDFC CREDIT)|To (SBI Card|CRED|HDFC Credit)"""
)

/** What one format extracts: amount, direction, account digits, party, ref, kind. */
private data class Hit(
    val amount: String, val dir: Direction, val last: String, val party: String,
    val ref: String = "", val kind: Kind = Kind.NORMAL,
)

private val D = Direction.DEBIT
private val C = Direction.CREDIT

private class Format(val name: String, pattern: String, val build: (List<String>) -> Hit) {
    val regex = rx(pattern)
}

private fun last4(s: String) = s.takeLast(4)

private val PATTERNS = listOf(
    // ---- HDFC
    Format("hdfc_paid", """Paid Rs\.?\s?([\d,.]+) For: (.+?) From HDFC Bank A/c XX(\d+)""") { g -> Hit(g[1], D, g[3], g[2]) },
    Format("hdfc_sent", """Sent Rs\.?\s?([\d,.]+) From HDFC Bank A/C \*(\d+) To (.+?) On """) { g -> Hit(g[1], D, g[2], g[3]) },
    Format("hdfc_card_spend", """Spent Rs\.?\s?([\d,.]+) On HDFC Bank Card (\d{4}) At (.+?) On \d{4}""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("hdfc_card_spend2", """Rs\.?\s?([\d,.]+) spent on HDFC Bank Card x(\d{4}) at (.+?) on \d{4}""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("hdfc_card_spend_old", """spent Rs\.?\s?([\d,.]+) On HDFC Bank (?:CREDIT|Debit) Card xx(\d{4}) At (.+?) On \d{4}""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("hdfc_autopay", """AutoPay \(E-mandate\) Success\S* For (.+?) (?:Current )?Txn Amt:\s?INR\s?([\d,.]+).*?(?:CC|Card xx) ?(\d{4})""") { g -> Hit(g[2], D, g[3], g[1]) },
    Format("hdfc_upi_debit", """Rs\.?\s?([\d,.]+) debited from a/c \*\*(\d+) on [\d-]+ to (?:VPA )?(.+?)\s?\(UPI""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("hdfc_refund", """Rs\.?\s?([\d,.]+) refunded by (.+?) on [\w/]+ & adjusted against HDFC Bank Credit Card (\d{4})""") { g -> Hit(g[1], C, g[3], g[2]) },
    Format("hdfc_rtgs_out", """RTGS Money Deposited~INR ([\d,.]+)~To (.+?)~Txn No: (\w+)""") { g -> Hit(g[1], D, "", g[2], g[3]) },
    Format("hdfc_received", """Received! INR ([\d,.]+) in HDFC Bank A/c xx(\d+) On .*? For (?:IMPS|NEFT|UPI) -(.+?)- (\d+)""") { g -> Hit(g[1], C, g[2], g[3], g[4]) },
    Format("hdfc_deposited", """INR ([\d,.]+) deposited in HDFC Bank A/c XX(\d+) on .*? for (?:NEFT|IMPS|RTGS) Cr-[^-]+-(.+?)\.Avl""") { g -> Hit(g[1], C, g[2], g[3]) },
    Format("hdfc_credit_alert", """Rs\.?\s?([\d,.]+) credited to HDFC Bank A/c XX(\d+) on [\d-]+ from (?:VPA )?(.+?) \((?:UPI )?(\d+)""") { g -> Hit(g[1], C, g[2], g[3], g[4]) },
    Format("card_reversal", """Transaction Reversed!.*?Card xx(\d{4}) Amt: Rs\.?\s?([\d,.]+) By (\S+)""") { g -> Hit(g[2], C, g[1], g[3], "", Kind.REVERSAL) },
    // ---- IOB
    Format("iob_payee_debit", """a/c X+(\d+) debited for payee (.+?) for Rs\.?\s?([\d,.]+) on [\d-]+, ref (\d+)""") { g -> Hit(g[3], D, g[1], g[2].trim(), g[4]) },
    Format("iob_imps_out", """A/C:X+(\d+) is debited for Rs\.?\s?([\d,.]+) on .*?a/c X+(\d+) - \(a/c holder name-(.+?)\) credited.*?Ref id:(\d+)""") { g -> Hit(g[2], D, g[1], "${g[4]} ••${last4(g[3])}", g[5]) },
    Format("iob_imps_in", """A/C:X+(\d+) is credited by Rs\.?\s?([\d,.]+) on .*? from a/c X+(\d+) \(IMPS Ref id:(\d+)""") { g -> Hit(g[2], C, g[1], "From a/c ••${last4(g[3])}", g[4]) },
    Format("iob_sb_debit", """Rs\.?\s?([\d,.]+) Debited (?:from|to) SB-x+(\d+) .*?\[(.+?)\]""") { g -> Hit(g[1], D, g[2], g[3].trim(), g[3].filter { it.isDigit() }) },
    Format("iob_sb_credit", """Rs\.?\s?([\d,.]+) Credited to SB-x+(\d+) .*?\[(.+?)\]""") { g -> Hit(g[1], C, g[2], g[3].trim()) },
    Format("iob_upi_credit", """a/c no\. X+(\d+) is credited by Rs\.?\s?([\d,.]+) on .*? from (.+?)\(UPI Ref no (\d+)""") { g -> Hit(g[2], C, g[1], g[3].trim(' ', ','), g[4]) },
    // ---- SBI
    Format("sbi_imps_credit", """a/c no\. X+(\d+) is credited by Rs\.?\s?([\d,.]+) on .*?mobile \S+?-(.+?) \(IMPS Ref# (\d+)""") { g -> Hit(g[2], C, g[1], g[3], g[4]) },
    Format("sbi_imps_debit", """a/c no\. X+(\d+) is debited for Rs\.?\s?([\d,.]+) on [\d-]+ and a/c X+(\d+) credited \(IMPS Ref no (\d+)""") { g -> Hit(g[2], D, g[1], "To a/c ••${last4(g[3])}", g[4]) },
    Format("sbi_yono_debit", """Your Ac x+(\d+) debited Rs\.?\s?([\d,.]+) for transfer to (.+?) Ac x+(\d+)""") { g -> Hit(g[2], D, g[1], "${g[3]} ••${last4(g[4])}") },
    Format("sbi_cbs_debit", """Your A/C X+(\d+) Debited INR ([\d,.]+) on [\d/]+ -Transferred to (.+?)\. Avl""") { g -> Hit(g[2], D, g[1], g[3]) },
    Format("sbi_loan_credit", """loan A/C X+(\d+) Credited INR ([\d,.]+) on [\d/]+,\s*deposit transfer from (.+?)\.-SBI""") { g -> Hit(g[2], C, g[1], g[3]) },
    Format("sbi_card_spend", """Rs\.?\s?([\d,.]+) spent on your SBI Credit Card ending (\d{4}) at (.+?) on """) { g -> Hit(g[1], D, g[2], g[3]) },
    // ---- IndusInd
    Format("indusind_debit", """INR ([\d,.]+) debited from IndusInd Bank A/c X+(\d+) to (.+?) on .*?RRN (\d+)""") { g -> Hit(g[1], D, g[2], g[3], g[4]) },
    Format("indusind_debit2", """account X+(\d+) debited with Rs\.?\s?([\d,.]+) on [\d-]+ and account X+(\d+)/(.+?) will be credited\.\(IMPS Ref no\. (\d+)""") { g -> Hit(g[2], D, g[1], "${g[4]} ••${last4(g[3])}", g[5]) },
    Format("indusind_credit", """IndusInd Account X+(\d+) has been credited for INR ([\d,.]+) towards (.+?)\. Call""") { g -> Hit(g[2], C, g[1], g[3].split("/").last()) },
    // ---- DCB
    Format("dcb_pos", """INR ([\d,.]+) debited DCB Bank a/c\*(\d+) POS/Ecom txn to (.+?) on""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("dcb_credit", """A/c no\. \*(\d+) is credited by INR ([\d,.]+)\.""") { g -> Hit(g[2], C, g[1], "Credit") },
    // ---- Pluxee meal card
    Format("pluxee_spend", """Rs\.?\s?([\d,.]+) spent from Pluxee\s+Meal Card wallet, card no\.\s?(?:xx(\d+))?\s?on .*? at (.+?)\s*\. Avl""") { g -> Hit(g[1], D, g[2], g[3]) },
    Format("pluxee_credit", """Pluxee Card has been successfully credited with Rs\.?\s?([\d,.]+) towards Meal Wallet""") { g -> Hit(g[1], C, "", "Meal allowance") },
)

// ---------------------------------------------------------------- categories
val DEBIT_CATEGORIES = listOf(
    "Food & dining", "Groceries", "Shopping", "Travel", "Transport", "Fuel & vehicle", "Health", "Personal care",
    "Subscriptions & apps", "Entertainment", "Insurance", "Investments", "UPI to people", "Bank charges",
    "Own account (likely)", "Card bill payment", "Other",
)
val CREDIT_CATEGORIES = listOf(
    "Salary", "Refund", "Reversal", "Meal allowance", "From people (UPI)", "Bank transfer in", "Dividend",
    "Own account (likely)", "Other credit",
)

private val DEBIT_RULES = listOf(
    "Investments" to """zerodha|\bnse\b|clearing corp|smallcase|groww|upstox|mutual fund|\bsip\b|\bcoin\b""",
    "Insurance" to """insurance|\baia\b|\blic\b""",
    "Personal care" to """beauty|salon|health and glow|spa\b|parlour""",
    "Health" to """medic|\bmed\b|pharma|apollo|clinic|hospital|diagnostic|ultra ?sound|scan|\blab\b|lens|eye care|amaha|health""",
    "Groceries" to """big ?bas|bigba|innovative retail|\bsupe\b|super ?mar|supermarket|spar|fruit|dmart|more retail|ratnadeep|zepto|blinkit|instamart|grofers""",
    "Food & dining" to """swiggy|zomato|eternal|coffee|cafe|kalanjiyam|restaurant|hotel|bakery|sweets|saravana|sri sarava|silver spoon|elior|catering|canteen|culinary""",
    "Shopping" to """nykaa|amazon|asspl|flipkart|myntra|naidu hall|decathlon|ajio|zudio|centro|lifestyle|usha internation|westside|silks|cosmoglitz""",
    "Fuel & vehicle" to """fuel|petrol|pump|auto cent|indian oil|hpcl|bpcl|asarauto""",
    "Transport" to """metro|uber|\bola\b|rapido|irctc""",
    "Travel" to """\bpte\b|global sim|airline|indigo|makemytrip|mmt|agoda|booking\.com|ibibo|tours|travel|trip\.com""",
    "Entertainment" to """bookmyshow|pvr|inox|netflix|hotstar|spotify""",
    "Subscriptions & apps" to """google|apple|youtube|airtel|jio|recharge|anthropic|openai""",
    "Bank charges" to """charg|chrgs|\bfee\b|gst""",
).map { (c, p) -> c to rx(p) }

private val PERSON = rx("""^(mr|ms|mrs)?\.?\s*[a-z][a-z .]{1,24}$""")
private val CARD_FORMATS = setOf("hdfc_card_spend", "hdfc_card_spend2", "hdfc_card_spend_old", "hdfc_autopay", "hdfc_refund", "card_reversal", "sbi_card_spend")

/** What the parser knows about the phone's owner; built from their SMS and Settings. */
class Owner(names: List<String> = emptyList()) {
    /** The owner's name as banks write it, e.g. "Asha K" (from Settings). Matches "A Asha", "AshaK", etc. */
    private val nameRegex: Regex? = names.map { it.trim() }.filter { it.length >= 3 }
        .flatMap { n -> n.split(" ").filter { it.length >= 4 } + n.replace(" ", "") }
        .distinct().takeIf { it.isNotEmpty() }
        ?.let { parts -> rx(parts.joinToString("|") { Regex.escape(it) }) }
    /** Last 4 digits of the owner's own accounts, detected from their SMS. */
    var accountEndings: Set<String> = emptySet()

    fun isOwn(party: String): Boolean {
        if (nameRegex?.containsMatchIn(party) == true) return true
        // Banks mask the other account differently ("••5678", "••678"), so compare endings.
        return Regex("••(\\d{2,4})").findAll(party).any { m -> accountEndings.any { it.endsWith(m.groupValues[1]) } }
    }
}

fun merchantKey(party: String): String = party.lowercase().filter { it in 'a'..'z' }.take(20)

fun autoCategory(t: Txn, owner: Owner = Owner()): String {
    if (t.kind == Kind.CARD_PAYMENT) return "Card bill payment"
    if (t.kind == Kind.REVERSAL) return "Reversal"
    val own = owner.isOwn(t.party)
    if (t.direction == C) {
        if (t.format == "hdfc_refund") return "Refund"
        if (t.format == "pluxee_credit") return "Meal allowance"
        if (t.format == "indusind_credit" && !own) return "Salary"
        if (own) return "Own account (likely)"
        if (t.format == "iob_upi_credit" || "@" in t.party || t.party.startsWith("UPI/")) return "From people (UPI)"
        if (Regex("^(NEFT|RTGS|IMPS)").containsMatchIn(t.party)) return "Bank transfer in"
        if (t.format == "iob_sb_credit" && Regex("^[A-Z][A-Z .]{2,12}$").matches(t.party)) return "Dividend"
        return "Other credit"
    }
    if (own) return "Own account (likely)"
    for ((name, r) in DEBIT_RULES) if (r.containsMatchIn(t.party)) return name
    if (t.accountType == "meal card") return "Food & dining"
    val payeeFmt = t.format == "iob_payee_debit" || t.merged.any { it.format == "iob_payee_debit" }
    if ("@" in t.party || (payeeFmt && PERSON.matches(t.party))) return "UPI to people"
    return "Other"
}

// ---------------------------------------------------------------- helpers
fun senderEntity(sender: String): String? {
    val a = sender.trim()
    val parts = a.split("-")
    val (ent, route) = when {
        parts.size >= 2 -> parts[1] to (if (parts.size > 2) parts[2] else "")
        a.length == 9 && a[0].isLetter() && a[1].isLetter() -> a.substring(2, 8) to a.substring(8)
        else -> return null
    }
    if (route.equals("P", ignoreCase = true)) return null       // promotional route
    val e = ent.uppercase()
    return if (e in BANK_ENTITIES) e else null
}

fun normalize(body: String): String = body.replace(Regex("\\s+"), " ").trim()

fun fingerprint(body: String): String {
    val md = MessageDigest.getInstance("SHA-1").digest(normalize(body).lowercase().toByteArray())
    return md.joinToString("") { "%02x".format(it) }.take(16)
}

private fun accountType(format: String) = when {
    format in CARD_FORMATS -> "card"
    format == "sbi_loan_credit" -> "loan"
    format.startsWith("pluxee") -> "meal card"
    else -> "a/c"
}

/**
 * Gives every row a readable account label ("HDFC card ••1234", "IOB a/c ••5678") using only the
 * digits in the SMS. Banks mask differently ("XXXXX78", "SB-xxx5678"), so short endings are matched
 * to a full 4-digit account of the same bank; an SMS with no digits uses the bank's only account.
 */
private fun labelAccounts(txns: List<Txn>, owner: Owner) {
    val full = txns.filter { it.accountDigits.length >= 4 }
        .map { Triple(it.bank, it.accountType, it.accountDigits.takeLast(4)) }.toSet()
    owner.accountEndings = full.map { it.third }.toSet()
    for (t in txns) {
        if (t.accountType == "meal card") { t.account = "${t.bank} meal card"; continue }
        val d = t.accountDigits
        val last4 = when {
            d.length >= 4 -> d.takeLast(4)
            d.isNotEmpty() -> full.filter { it.first == t.bank && it.second == t.accountType && it.third.endsWith(d) }
                .map { it.third }.distinct().singleOrNull() ?: d
            else -> full.filter { it.first == t.bank && it.second == t.accountType }.map { it.third }.distinct().singleOrNull()
        }
        val name = if (t.accountType == "card" && t.bank.endsWith(" Card")) "${t.bank.removeSuffix(" Card")} card" else "${t.bank} ${t.accountType}"
        t.account = if (last4 != null) "$name ••$last4" else name
    }
}

private fun cleanParty(p: String) = p.trim().replace(Regex("^\\.+|_+$"), "").trim()

private fun parseAmount(s: String) = s.replace(",", "").trimEnd('.').toDouble()

// ---------------------------------------------------------------- main entry
/**
 * @param seen fingerprints already processed in this run (updated in place).
 * @param rowCategory user's category for one row (row id -> category).
 * @param merchantCategory user's category for every row of a merchant (merchant key -> category).
 */
fun parse(
    messages: List<Sms>,
    owner: Owner = Owner(),
    seen: MutableSet<String> = mutableSetOf(),
    rowCategory: Map<String, String> = emptyMap(),
    merchantCategory: Map<String, String> = emptyMap(),
): ParseResult {
    val txns = mutableListOf<Txn>()
    val skipped = mutableListOf<Skipped>()
    for (sms in messages.sortedBy { it.time }) {
        val body = normalize(sms.body)
        val fp = fingerprint(body)
        if (!seen.add(fp)) { skipped += Skipped(sms, "Same SMS already processed"); continue }
        val ent = senderEntity(sms.sender)
        if (ent == null) { skipped += Skipped(sms, "Not a bank sender"); continue }
        val skip = SKIP.firstOrNull { it.first.containsMatchIn(body) }
        if (skip != null) { skipped += Skipped(sms, skip.second); continue }
        var matched = false
        for (f in PATTERNS) {
            val m = f.regex.find(body) ?: continue
            matched = true
            val h = f.build(m.groupValues)
            val t = Txn(
                time = sms.time, amount = parseAmount(h.amount), direction = h.dir,
                account = "", bank = BANK_ENTITIES.getValue(ent), accountDigits = h.last.trim(), accountType = accountType(f.name),
                party = cleanParty(h.party), kind = h.kind, ref = h.ref, format = f.name, fingerprint = fp, text = body,
            )
            if (t.time < START_DATE) skipped += Skipped(sms, "Before 1 Aug 2026") else txns += t
            break
        }
        if (!matched) skipped += Skipped(sms, "Not a transaction format we read")
    }
    labelAccounts(txns, owner)
    val rows = mergeDuplicates(txns.sortedBy { it.time })
    for (t in rows) {
        if (t.kind == Kind.NORMAL && (listOf(t) + t.merged).any { CARD_PAYMENT.containsMatchIn(it.text) }) {
            t.kind = Kind.CARD_PAYMENT
        }
    }
    for (t in rows) {
        t.category = rowCategory[t.id] ?: merchantCategory[merchantKey(t.party)] ?: autoCategory(t, owner)
    }
    return ParseResult(rows, skipped)
}

/**
 * Banks send 2-3 SMS for one payment (IOB 'debited for payee' + 'Debited from SB';
 * SBI YONO + CBS; HDFC 'Spent' + 'AutoPay Success'). Same account, amount and
 * direction, different SMS format, within 36 h, no conflicting reference = one row.
 */
private fun mergeDuplicates(txns: List<Txn>): List<Txn> {
    val out = mutableListOf<Txn>()
    for (t in txns) {
        var dup: Txn? = null
        for (o in out.asReversed()) {
            if (java.time.Duration.between(o.time, t.time).toHours() > 36) break
            if (o.account == t.account && o.direction == t.direction && kotlin.math.abs(o.amount - t.amount) < 0.01 &&
                o.kind != Kind.REVERSAL && t.kind != Kind.REVERSAL && o.format != t.format &&
                o.merged.none { it.format == t.format }
            ) {
                val refsOk = o.ref.isEmpty() || t.ref.isEmpty() || o.ref.startsWith(t.ref) || t.ref.startsWith(o.ref)
                if (refsOk) { dup = o; break }
            }
        }
        if (dup != null) {
            val generic = listOf("UPI/", "IBR/", "RTGS-", "Debit Card")
            if (generic.any { dup.party.startsWith(it) } && generic.none { t.party.startsWith(it) }) {
                dup.party = t.party
                if (t.ref.isNotEmpty()) dup.ref = t.ref
            }
            dup.merged += t
        } else out += t
    }
    return out
}
