package dev.kamlendu.preptracker.expense

import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One purchase, many messages. These are the shapes that actually reached the app.
 */
class DeduperTest {

    private val t = 1_726_000_000_000L

    private fun row(
        amount: Long,
        at: Long,
        merchant: String? = null,
        source: ExpenseSource = ExpenseSource.NOTIFICATION,
        fingerprint: String = "txn:$amount:$at",
        locked: Boolean = false,
        id: Long = 0,
    ) = Expense(
        id = id,
        dayKey = "2026-09-19",
        amountPaise = amount,
        merchant = merchant,
        category = "OTHER",
        occurredAt = at,
        source = source.id,
        fingerprint = fingerprint,
        categoryLocked = locked,
    )

    /** The Swiggy order paid with Amazon Pay: two emails, same amount, different wording. */
    @Test
    fun `two emails about one purchase are one transaction`() {
        val payment = row(23_200, t, "Swiggy Food APL")
        val orderConfirmation = row(23_200, t + 40_000, "using Amazon Pay Later. Total")
        assertTrue(Deduper.isSameTransaction(payment, orderConfirmation))
    }

    @Test
    fun `bank SMS and wallet email about one purchase are one transaction`() {
        val sms = row(23_200, t, "SWIGGY", ExpenseSource.SMS)
        val email = row(23_200, t + 9 * 60_000, "Swiggy Food APL", ExpenseSource.NOTIFICATION)
        assertTrue(Deduper.isSameTransaction(sms, email))
    }

    /** Two references from the bank mean two payments, however alike they look. */
    @Test
    fun `different bank references are never merged`() {
        val first = row(5_000, t, "CHAI POINT", ExpenseSource.SMS, "ref:111111111111:5000")
        val second = row(5_000, t + 60_000, "CHAI POINT", ExpenseSource.SMS, "ref:222222222222:5000")
        assertFalse(Deduper.isSameTransaction(first, second))
    }

    @Test
    fun `the same reference is merged even across sources`() {
        val sms = row(5_000, t, "CHAI POINT", ExpenseSource.SMS, "ref:111111111111:5000")
        val app = row(5_000, t + 11 * 60_000, "Chai Point", ExpenseSource.NOTIFICATION, "ref:111111111111:5000")
        assertTrue(Deduper.isSameTransaction(sms, app))
    }

    @Test
    fun `two separate payments of the same amount hours apart are kept`() {
        val morning = row(2_000, t, "CHAI POINT")
        val evening = row(2_000, t + 6 * 60 * 60_000, "CHAI POINT")
        assertFalse(Deduper.isSameTransaction(morning, evening))
    }

    @Test
    fun `different amounts are never merged`() {
        assertFalse(Deduper.isSameTransaction(row(2_000, t), row(2_500, t + 1_000)))
    }

    @Test
    fun `unrelated payees far apart in the window are kept`() {
        val first = row(10_000, t, "ANAND BAZAR", ExpenseSource.SMS)
        val second = row(10_000, t + 12 * 60_000, "PETROL PUMP", ExpenseSource.SMS)
        assertFalse(Deduper.isSameTransaction(first, second))
    }

    @Test
    fun `merchant matching ignores wrapper words and punctuation`() {
        assertTrue(Deduper.merchantsAgree("Swiggy Food APL", "SWIGGY"))
        assertTrue(Deduper.merchantsAgree("Amount Swiggy Food APL", "Swiggy Food APL"))
        assertFalse(Deduper.merchantsAgree("ANAND BAZAR", "PETROL PUMP"))
        // "payment" alone proves nothing.
        assertFalse(Deduper.merchantsAgree("Payment Gateway", "Payment Services"))
    }

    @Test
    fun `a hand-corrected row survives the sweep`() {
        val auto = row(23_200, t, "Swiggy Food APL", id = 1)
        val corrected = row(23_200, t + 30_000, "Swiggy", locked = true, id = 2)
        assertTrue(Deduper.preferred(auto, corrected) === corrected)
    }

    /** Of the two Swiggy rows, the one naming the restaurant is worth more than the one naming the wallet. */
    @Test
    fun `the row that names the payee is the one kept`() {
        val wording = row(23_200, t, "using Amazon Pay Later. Total", id = 1)
        val payee = row(23_200, t + 40_000, "Swiggy Food APL", id = 2)
        assertTrue(Deduper.preferred(wording, payee) === payee)
    }

    @Test
    fun `the row with a bank reference is the one kept`() {
        val withRef = row(23_200, t + 30_000, "SWIGGY", ExpenseSource.SMS, "ref:999999999999:23200", id = 2)
        val withoutRef = row(23_200, t, "Swiggy Food APL", id = 1)
        assertTrue(Deduper.preferred(withoutRef, withRef) === withRef)
    }
}
