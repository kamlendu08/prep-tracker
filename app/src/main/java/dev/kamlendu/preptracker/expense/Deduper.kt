package dev.kamlendu.preptracker.expense

import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseSource
import kotlin.math.abs

/**
 * Decides whether two captured rows are the same real-world payment.
 *
 * One purchase routinely generates several messages: the bank's SMS, the wallet's email, the
 * payment app's notification, and often an order-confirmation on top — for a single Swiggy order
 * paid with Amazon Pay, two separate emails both quoted ₹232 and each one named a different
 * "merchant". A fingerprint can only catch the cases where the texts agree, so the real defence
 * has to be this: same amount, close in time, no evidence they are distinct.
 *
 * The bias is deliberate. Missing one genuine second payment of the identical amount, minutes
 * apart, is recoverable by hand; silently counting one payment twice quietly corrupts the budget
 * and is not visible until the numbers stop making sense.
 */
object Deduper {

    /**
     * Same amount inside this gap is treated as one payment whatever the texts say — two distinct
     * payments of an identical amount within five minutes is vanishingly rare next to the
     * certainty that banks and wallets send several messages about one.
     */
    const val TIGHT_WINDOW_MS = 5 * 60_000L

    /** Beyond that, they still merge if there is positive evidence they are the same. */
    const val LOOSE_WINDOW_MS = 15 * 60_000L

    /** Words too generic to prove two merchant strings describe the same payee. */
    private val NOISE_TOKENS = setOf(
        "the", "and", "for", "from", "with", "pay", "payment", "amount", "total", "using", "upi",
        "bank", "card", "india", "ltd", "pvt", "limited", "private", "order", "successful", "your",
    )

    fun isSameTransaction(existing: Expense, candidate: Expense): Boolean {
        if (existing.amountPaise != candidate.amountPaise) return false

        // A bank reference is authoritative in both directions: two different references are two
        // different payments, however similar they look.
        val existingRef = referenceOf(existing.fingerprint)
        val candidateRef = referenceOf(candidate.fingerprint)
        if (existingRef != null && candidateRef != null) return existingRef == candidateRef

        val gap = abs(existing.occurredAt - candidate.occurredAt)
        if (gap > LOOSE_WINDOW_MS) return false
        if (gap <= TIGHT_WINDOW_MS) return true

        // Further apart, only merge on evidence: the payees agree, one side never identified a
        // payee, or the two arrived through different pipes (an SMS and an email about one spend).
        return merchantsAgree(existing.merchant, candidate.merchant) ||
            existing.source != candidate.source
    }

    fun merchantsAgree(a: String?, b: String?): Boolean {
        val left = normalise(a)
        val right = normalise(b)
        if (left.isBlank() || right.isBlank()) return true
        if (left == right) return true
        if (left.contains(right) || right.contains(left)) return true
        return significantTokens(left).any { it in significantTokens(right) }
    }

    /** Pulls the bank reference back out of a fingerprint of the form `ref:<digits>:<amount>`. */
    private fun referenceOf(fingerprint: String): String? =
        fingerprint.takeIf { it.startsWith("ref:") }?.split(":")?.getOrNull(1)

    private fun normalise(value: String?): String =
        (value ?: "").lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun significantTokens(value: String): Set<String> =
        value.split(" ").filter { it.length >= 4 && it !in NOISE_TOKENS }.toSet()

    /** How much of a merchant string is actually a name rather than transaction wording. */
    private fun informativeness(merchant: String?): Int {
        if (merchant.isNullOrBlank()) return 0
        val normalised = normalise(merchant)
        val words = normalised.split(" ").filter { it.isNotBlank() }
        if (words.isEmpty()) return 0
        val noise = words.count { it in NOISE_TOKENS }
        return (words.size - noise) * 2 - noise
    }

    /**
     * Of two rows describing one payment, the one worth keeping: a hand-corrected row always wins,
     * then one carrying a bank reference, then the one that actually identified a payee, then
     * whichever arrived first.
     */
    fun preferred(a: Expense, b: Expense): Expense {
        if (a.categoryLocked != b.categoryLocked) return if (a.categoryLocked) a else b
        val aRef = referenceOf(a.fingerprint) != null
        val bRef = referenceOf(b.fingerprint) != null
        if (aRef != bRef) return if (aRef) a else b
        // Both name someone: prefer whichever says more about the payee. "Swiggy Food APL" beats
        // "using Amazon Pay Later. Total", which is wording, not a merchant.
        val aScore = informativeness(a.merchant)
        val bScore = informativeness(b.merchant)
        if (aScore != bScore) return if (aScore > bScore) a else b
        val aManual = a.source == ExpenseSource.MANUAL.id
        val bManual = b.source == ExpenseSource.MANUAL.id
        if (aManual != bManual) return if (aManual) a else b
        return if (a.occurredAt <= b.occurredAt) a else b
    }
}
