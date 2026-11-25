package com.smsledger.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Rule tests with made-up SMS (no real account numbers or names). */
class ParserTest {
    private fun sms(sender: String, at: String, body: String) = Sms(sender, LocalDateTime.parse(at), body)

    private val iobPayee = sms("VM-IOBCHN-S", "2026-09-05T10:00:05",
        "Your a/c XXXXX78 debited for payee SWIGGY for Rs. 250.00 on 2026-09-05, ref 612345678901.If not you, report to your bank immediately-IOB.")
    private val iobGeneric = sms("VA-IOBCHN-T", "2026-09-05T10:00:09",
        "Rs.250.00 Debited from SB-xxx5678 AcBal:1000.00 CLRBal: 1000.00 [UPI/612345 ] CHENNAI on 05-09-2026 10:00:00.IOB.")
    private val cardSpend = sms("AD-HDFCBK-S", "2026-09-06T10:00:00",
        "Spent Rs.500 On HDFC Bank Card 1234 At SHOPX On 2026-09-06:10:00:00.Not You? To Block+Reissue Call 18002586161")
    private val cardReversal = sms("JM-HDFCBK-S", "2026-09-06T10:01:00",
        "Transaction Reversed!On HDFC Bank CREDIT Card xx1234 Amt: Rs.500 By SHOPX On 2026-09-06:10:00:55")
    private val cardBill = sms("VD-HDFCBK-S", "2026-09-07T09:00:00",
        "Alert! Paid Rs. 20,000.00 For: Credit Card payment From HDFC Bank A/c XX4321 Via Online Banking.")
    private val cardSideReceipt = sms("AX-HDFCBK-S", "2026-09-07T09:00:02",
        "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 20000.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 1234 ON 7-9-2026.YOUR AVAILABLE LIMIT IS RS. 100000.00")
    private val otp = sms("JM-HDFCBK-S", "2026-09-07T09:05:00",
        "OTP is 123456 for txn of INR 999.00 at SHOPY on HDFC Bank card ending 1234. Do not share OTP")
    private val beforeStart = sms("AD-HDFCBK-S", "2026-07-30T12:00:00",
        "Spent Rs.300 On HDFC Bank Card 1234 At SHOPZ On 2026-07-30:12:00:00.Not You?")
    private val exactlyTenK = sms("JK-INDUSB-T", "2026-09-08T12:00:00",
        "INR 10000 debited from IndusInd Bank A/c XXXXXXX9999 to Asha K on 08-09-2026. RRN 612300000001. Not you? Call 18602677777")
    private val promo = sms("VD-HDFCBK-P", "2026-09-08T12:00:00",
        "Spent Rs.700 On HDFC Bank Card 1234 At PROMO On 2026-09-08:12:00:00.")
    private val zerodha = sms("VA-IOBCHN-S", "2026-09-09T12:00:00",
        "Your a/c XXXXX78 debited for payee ZERODHA BROKING LTD for Rs. 50000.00 on 2026-09-09, ref 612399999999.If not you, report to your bank immediately-IOB.")

    private val all = listOf(iobPayee, iobGeneric, cardSpend, cardReversal, cardBill, cardSideReceipt, otp, beforeStart, exactlyTenK, promo, zerodha)
    private val result = parse(all, Owner(listOf("Asha K")))
    private val txns = result.txns

    @Test fun repeatSmsForOnePaymentIsOneRow() {
        val swiggy = txns.filter { it.amount == 250.0 }
        assertEquals(1, swiggy.size)
        assertEquals(2, swiggy[0].smsCount)
        assertEquals("SWIGGY", swiggy[0].party)
        assertEquals("IOB a/c ••5678", swiggy[0].account)       // "XXXXX78" matched to the full account ending
    }

    @Test fun sameSmsTwiceIsCountedOnce() {
        val again = parse(all + iobPayee.copy(time = iobPayee.time.plusDays(1)), Owner())
        assertEquals(1, again.txns.count { it.amount == 250.0 })
        assertTrue(again.skipped.any { it.reason == "Same SMS already processed" })
    }

    @Test fun reversedCardPaymentIsKeptAsCredit() {
        val shop = txns.filter { it.party == "SHOPX" }
        assertEquals(listOf(Direction.DEBIT, Direction.CREDIT), shop.map { it.direction })
        assertEquals("Reversal", shop[1].category)
    }

    @Test fun cardBillIsShownButNotCounted() {
        val bill = txns.single { it.kind == Kind.CARD_PAYMENT }
        assertEquals(20000.0, bill.amount, 0.0)
        assertFalse(bill.counted)
        assertTrue(result.skipped.any { it.sms == cardSideReceipt })
    }

    @Test fun otpPromoAndPreAugustAreIgnored() {
        assertTrue(txns.none { it.amount == 999.0 || it.amount == 700.0 || it.amount == 300.0 })
    }

    @Test fun exactlyTenThousandIsUnder10k() {
        val t = txns.single { it.amount == 10000.0 }
        assertFalse(t.over10k)
        assertTrue(txns.single { it.amount == 50000.0 }.over10k)
    }

    @Test fun categoriesAreFilledIn() {
        assertEquals("Own account (likely)", txns.single { it.amount == 10000.0 }.category)   // name from Settings
        assertEquals("Investments", txns.single { it.amount == 50000.0 }.category)
        assertEquals("Food & dining", txns.single { it.amount == 250.0 }.category)
    }

    @Test fun userCategoryWins() {
        val swiggy = txns.single { it.amount == 250.0 }
        val byRow = parse(all, Owner(), rowCategory = mapOf(swiggy.id to "Other")).txns.single { it.amount == 250.0 }
        assertEquals("Other", byRow.category)
        val byMerchant = parse(all, Owner(), merchantCategory = mapOf(merchantKey("SWIGGY") to "Groceries")).txns.single { it.amount == 250.0 }
        assertEquals("Groceries", byMerchant.category)
    }
}
