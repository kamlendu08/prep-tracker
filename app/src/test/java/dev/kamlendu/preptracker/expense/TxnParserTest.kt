package dev.kamlendu.preptracker.expense

import dev.kamlendu.preptracker.data.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser decides what lands in the budget, so the messages it must get right — and the ones it
 * must refuse — are pinned here. Every sample is in the shape Indian banks and UPI apps actually
 * send.
 */
class TxnParserTest {

    private val now = 1_726_000_000_000L

    @Test
    fun `reads a bank debit with a UPI handle`() {
        val parsed = TxnParser.parseText(
            "Dear Customer, Rs.250.00 debited from A/c XX1234 on 12-09-26 to VPA swiggy@icici " +
                "Ref No 123456789012. Not you? Call 18001234.",
            now,
        )
        assertNotNull(parsed)
        assertEquals(25_000L, parsed!!.amountPaise)
        assertEquals("swiggy@icici", parsed.merchant)
        assertEquals(ExpenseCategory.FOOD, parsed.category)
    }

    @Test
    fun `reads a UPI app notification and trims the reference out of the merchant`() {
        val parsed = TxnParser.parseText("Paid ₹120 to Blinkit UPI Ref 987654321098", now)
        assertNotNull(parsed)
        assertEquals(12_000L, parsed!!.amountPaise)
        assertEquals("Blinkit", parsed.merchant)
        assertEquals(ExpenseCategory.GROCERIES, parsed.category)
    }

    @Test
    fun `handles thousands separators and paise`() {
        val parsed = TxnParser.parseText("INR 1,250.50 spent on card at Reliance Fresh", now)
        assertNotNull(parsed)
        assertEquals(125_050L, parsed!!.amountPaise)
        assertEquals(ExpenseCategory.GROCERIES, parsed.category)
    }

    @Test
    fun `reads an ATM withdrawal`() {
        val parsed = TxnParser.parseText(
            "Rs 2000 withdrawn from A/c XX99 at ATM SBI MG ROAD on 12-09-26.",
            now,
        )
        assertNotNull(parsed)
        assertEquals(200_000L, parsed!!.amountPaise)
        assertTrue(parsed.merchant!!.startsWith("ATM SBI"))
    }

    /**
     * The same debit reaching us as a bank SMS and as the UPI app's notification must produce one
     * fingerprint, or the day's spend doubles.
     */
    @Test
    fun `the same transaction from two sources shares a fingerprint`() {
        val sms = TxnParser.parseText(
            "Rs.120.00 debited from A/c XX1234 to VPA blinkit@ybl Ref No 987654321098",
            now,
        )
        val notification = TxnParser.parseText("Paid ₹120 to Blinkit UPI Ref 987654321098", now + 8_000)
        assertNotNull(sms)
        assertNotNull(notification)
        assertEquals(sms!!.fingerprint, notification!!.fingerprint)
    }

    /** Without a reference number, the window is what stops a double count. */
    @Test
    fun `duplicates without a reference still collide inside the window`() {
        val first = TxnParser.parseText("Rs.60 paid to Chai Point", now)
        val second = TxnParser.parseText("Rs.60 paid to Chai Point", now + 30_000)
        assertEquals(first!!.fingerprint, second!!.fingerprint)
    }

    @Test
    fun `ignores an OTP`() {
        assertNull(
            TxnParser.parseText(
                "123456 is your OTP for a transaction of Rs.500 at Amazon. Do not share.",
                now,
            )
        )
    }

    @Test
    fun `ignores money coming in`() {
        assertNull(
            TxnParser.parseText("Rs.5000 credited to your A/c XX1234 on 12-09-26.", now)
        )
    }

    @Test
    fun `ignores a mandate that has not been charged yet`() {
        assertNull(
            TxnParser.parseText(
                "Your a/c will be debited with Rs.499 on 15-09-26 for Netflix e-mandate.",
                now,
            )
        )
    }

    @Test
    fun `ignores a failed payment`() {
        assertNull(
            TxnParser.parseText("Your payment of Rs.300 to Swiggy failed. Amount not debited.", now)
        )
    }

    @Test
    fun `ignores marketing that quotes a rupee figure`() {
        assertNull(
            TxnParser.parseText(
                "Congratulations! You are pre-approved for a loan of Rs.500000. Apply now.",
                now,
            )
        )
    }

    @Test
    fun `drops a message with no readable amount`() {
        assertNull(TxnParser.parseText("Your card was used for a purchase today.", now))
    }

    @Test
    fun `categorises study spending`() {
        assertEquals(
            ExpenseCategory.STUDY,
            TxnParser.categorise("paid to made easy publications", "MADE EASY"),
        )
        assertEquals(
            ExpenseCategory.TRANSPORT,
            TxnParser.categorise("debited at indian oil petrol pump", "INDIAN OIL"),
        )
        assertEquals(ExpenseCategory.OTHER, TxnParser.categorise("debited at XYZ traders", "XYZ"))
    }
}
