package dev.kamlendu.preptracker.expense

import dev.kamlendu.preptracker.data.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Axis and Kotak message templates, structurally verbatim.
 *
 * These are the shapes the app actually has to live with, so every one of them is pinned: the
 * amount, the payee, and — just as important — the ones that must be refused.
 *
 * Names, account digits, reference numbers and amounts are stand-ins: what the parser reads is the
 * *structure* — field order, separators, the `UPI/P2M/<ref>/<NAME>` path, the bare payee line above
 * "Avl Limit" — and every one of those is preserved exactly. Keeping real transactions here would
 * put one person's spending history in the repository for no test benefit.
 */
class RealBankSmsTest {

    private val now = 1_726_000_000_000L

    // ---------- Axis: UPI from the savings account ----------

    @Test
    fun `axis UPI to a person`() {
        val parsed = TxnParser.parseText(
            """
            INR 1.00 debited
            A/c no. XX1234
            19-09-26, 12:33:44
            UPI/P2A/100000000001/PRIYA SHARMA
            Not you? SMS BLOCKUPI Cust ID to 919999999999
            Axis Bank
            """.trimIndent(),
            now,
        )
        assertNotNull(parsed)
        assertEquals(100L, parsed!!.amountPaise)
        assertEquals("PRIYA SHARMA", parsed.merchant)
        // A transfer to a person is not a purchase — no keyword guessing on someone's name.
        assertEquals(ExpenseCategory.OTHER, parsed.category)
        assertEquals("ref:100000000001:100", parsed.fingerprint)
    }

    @Test
    fun `axis UPI to a merchant`() {
        val parsed = TxnParser.parseText(
            """
            INR 180.00 debited
            A/c no. XX1234
            16-09-26, 15:49:31
            UPI/P2M/100000000002/SUNRISE BAZAR FL
            Not you? SMS BLOCKUPI Cust ID to 919999999999
            Axis Bank
            """.trimIndent(),
            now,
        )
        assertNotNull(parsed)
        assertEquals(18_000L, parsed!!.amountPaise)
        assertEquals("SUNRISE BAZAR FL", parsed.merchant)
        assertEquals(ExpenseCategory.GROCERIES, parsed.category)
    }

    @Test
    fun `axis UPI to a food merchant`() {
        val parsed = TxnParser.parseText(
            """
            INR 45.00 debited
            A/c no. XX1234
            14-09-26, 13:44:15
            UPI/P2M/100000000003/corner hot dog
            Not you? SMS BLOCKUPI Cust ID to 919999999999
            Axis Bank
            """.trimIndent(),
            now,
        )
        assertEquals(4_500L, parsed!!.amountPaise)
        assertEquals("corner hot dog", parsed.merchant)
        assertEquals(ExpenseCategory.FOOD, parsed.category)
    }

    // ---------- Axis: credit card ----------

    @Test
    fun `axis card spend picks the amount not the available limit`() {
        val parsed = TxnParser.parseText(
            """
            Spent INR 10
            Axis Bank Card no. XX5678
            19-09-26 08:41:52 IST
            RAJESH KU
            Avl Limit: INR 2188.16
            Not you? SMS BLOCK 5678 to 919999999999
            """.trimIndent(),
            now,
        )
        assertNotNull(parsed)
        assertEquals(1_000L, parsed!!.amountPaise)
        assertEquals("RAJESH KU", parsed.merchant)
    }

    @Test
    fun `axis card spend with a three digit amount`() {
        val parsed = TxnParser.parseText(
            """
            Spent INR 365
            Axis Bank Card no. XX5678
            12-09-26 20:33:12 IST
            Riverside Mal
            Avl Limit: INR 3292.03
            Not you? SMS BLOCK 5678 to 919999999999
            """.trimIndent(),
            now,
        )
        assertEquals(36_500L, parsed!!.amountPaise)
        assertEquals("Riverside Mal", parsed.merchant)
    }

    // ---------- Kotak ----------

    @Test
    fun `kotak account transfer`() {
        val parsed = TxnParser.parseText(
            "Sent Rs.500.00 from Kotak Bank A/c X3456 to AMIT VERMA on 16-09-26. " +
                "UPI Ref 100000000004. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
            now,
        )
        assertNotNull(parsed)
        assertEquals(50_000L, parsed!!.amountPaise)
        assertEquals("AMIT VERMA", parsed.merchant)
        assertEquals("ref:100000000004:50000", parsed.fingerprint)
    }

    @Test
    fun `kotak credit card spend`() {
        val parsed = TxnParser.parseText(
            "INR 15 spent on Kotak Credit Card x9012 on 07-FEB-2026 at UPI-100000000005-RAVIK. " +
                "Avl limit INR 34759.75 Fraud? https://www.kotak.bank.in/KBANKT/querytxn",
            now,
        )
        assertNotNull(parsed)
        assertEquals(1_500L, parsed!!.amountPaise)
        // The payee is the tail after the UPI reference, not the reference itself.
        assertEquals("RAVIK", parsed.merchant)
    }

    /**
     * The message says "Kotak Credit Card" but this is a purchase, not a bill settlement. Filing
     * it as a card bill would drop it out of the daily total and under-report the day.
     */
    @Test
    fun `a purchase on a credit card is not a card bill`() {
        val parsed = TxnParser.parseText(
            "INR 30 spent on Kotak Credit Card x9012 on 18-09-26 at UPI-100000000005-CHAIWALA. " +
                "Avl limit INR 34729.75",
            now,
        )
        assertNotNull(parsed)
        assertNotEquals(ExpenseCategory.CARD_BILL, parsed!!.category)
    }

    // ---------- Must be refused ----------

    @Test
    fun `salary credit is not a spend`() {
        assertNull(
            TxnParser.parseText(
                "INR 75000.00 credited to Axis Bank A/c XX1234 on 17-09-26 at 18:26:54 IST. " +
                    "Ref: ACME CORP/SALARY.View bal: https://ccm.axis.bank.in/AXISBK/m49XJ3KM",
                now,
            )
        )
    }

    @Test
    fun `card bill reminder is not a spend`() {
        assertNull(
            TxnParser.parseText(
                "Payment of INR 10,176.70 on Kotak Credit Card x9012 is due on 23-09-26. " +
                    "Min due: INR 577.17. Tap to pay: https://kotak.bank.in/KBANKT/CCPMT Ignore if paid",
                now,
            )
        )
    }

    @Test
    fun `mandate created and revoked are not spends`() {
        assertNull(
            TxnParser.parseText(
                "Your UPI ASPRESENTED mandate has been successfully created towards Google " +
                    "from 11-09-26 to 31-12-36 for INR 149.00 - Axis Bank",
                now,
            )
        )
        assertNull(
            TxnParser.parseText(
                "Your UPI mandate has been successfully revoked towards Google for INR 149.00 - Axis Bank",
                now,
            )
        )
    }

    /**
     * Paying the card bill must not be counted: those purchases already hit the budget on the day
     * they were made. It is still recorded, just outside the daily total.
     */
    @Test
    fun `paying the credit card bill is filed as a card bill`() {
        val parsed = TxnParser.parseText(
            """
            INR 1500.00 debited
            A/c no. XX1234
            12-09-26, 11:31:47
            UPI/P2M/100000000006/CRED Club
            Not you? SMS BLOCKUPI Cust ID to 919999999999
            Axis Bank
            """.trimIndent(),
            now,
        )
        assertNotNull(parsed)
        assertEquals(150_000L, parsed!!.amountPaise)
        assertEquals(ExpenseCategory.CARD_BILL, parsed.category)
    }

    /** The same card spend seen as SMS and as a UPI-app notification must not count twice. */
    @Test
    fun `axis UPI debit dedups against the UPI app notification`() {
        val sms = TxnParser.parseText(
            """
            INR 25.00 debited
            A/c no. XX1234
            11-09-26, 17:34:56
            UPI/P2M/100000000007/GRAND SANCHI POINT
            Not you? SMS BLOCKUPI Cust ID to 919999999999
            Axis Bank
            """.trimIndent(),
            now,
        )
        val notification = TxnParser.parseText(
            "Paid ₹25 to Grand Sanchi Point UPI Ref 100000000007",
            now + 6_000,
        )
        assertNotNull(sms)
        assertNotNull(notification)
        assertEquals(sms!!.fingerprint, notification!!.fingerprint)
    }
}
