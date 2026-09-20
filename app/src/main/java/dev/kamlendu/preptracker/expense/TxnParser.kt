package dev.kamlendu.preptracker.expense

import dev.kamlendu.preptracker.data.Expense
import dev.kamlendu.preptracker.data.ExpenseCategory
import dev.kamlendu.preptracker.data.ExpenseSource
import dev.kamlendu.preptracker.data.dayKeyOf
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Turns a bank SMS or payment notification into an expense row.
 *
 * Deliberately conservative: a message it cannot confidently read is dropped rather than guessed
 * at, because a wrong number silently inflating the day's spend is worse than a missing one you
 * can add by hand. Everything here is local pattern matching — no message text ever leaves the
 * phone.
 *
 * The bank-specific patterns come first and are written against real Axis and Kotak messages,
 * because the generic "paid to X" shapes miss both of them: Axis puts the payee in a URL-ish path
 * (`UPI/P2M/<ref>/<NAME>`) or on a bare line above "Avl Limit", and Kotak writes "Sent Rs.X from
 * A/c to NAME".
 */
object TxnParser {

    private val IGNORE_CASE = setOf(RegexOption.IGNORE_CASE)

    /** "Rs.250", "Rs 1,250.50", "INR 250", "₹250" */
    private val AMOUNT = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        IGNORE_CASE,
    )

    /** Amount written after the verb: "debited by 250.00", "spent 250" */
    private val AMOUNT_TRAILING = Regex(
        """(?:debited|spent|paid|charged)\s+(?:by|for|with)?\s*(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        IGNORE_CASE,
    )

    private val DEBIT = Regex(
        """\b(debited|debit|spent|paid|withdrawn|withdrew|purchase|deducted|sent)\b""",
        IGNORE_CASE,
    )

    private val CREDIT = Regex(
        """\b(credited|received|refund(?:ed)?|reversal|reversed|cashback|deposited)\b""",
        IGNORE_CASE,
    )

    /**
     * Mentions money and a debit verb, but is not a completed debit: one-time passwords, balance
     * summaries, card bills that are merely *due*, mandates being set up or revoked, failures, and
     * the bank's own marketing.
     */
    private val NOT_A_TRANSACTION = Regex(
        """\b(otp|one[\s-]time\s*password|will\s+be\s+debited|is\s+due|due\s+on|min\s+due|mandate|""" +
            """autopay\s+set|failed|declined|unsuccessful|reversed|request(?:ed)?\s+(?:money|payment)|""" +
            """apply\s+now|pre-?approved|congratulations|statement\s+(?:is|has)|""" +
            """order\s+(?:successfully\s+)?placed|order\s+confirmed|""" +
            """available\s+bal(?:ance)?\s*:?\s*(?:rs|inr|₹)?[0-9,.\s]+$)\b""",
        IGNORE_CASE,
    )

    /** Axis UPI: `UPI/P2M/183473303265/ANAND BAZAR FL` — reference and payee in one path. */
    private val AXIS_UPI = Regex(
        """UPI/(P2A|P2M)/(\d{6,})/(.+?)(?=\s+Not\s+you|\s+Axis\s+Bank|\s*$)""",
        IGNORE_CASE,
    )

    /** Axis card: the merchant sits alone between the timestamp and "Avl Limit". */
    private val AXIS_CARD = Regex(
        """\d{2}:\d{2}:\d{2}(?:\s*IST)?\s+(.+?)\s+Avl\s*Limit""",
        IGNORE_CASE,
    )

    /** Kotak card: "at UPI-054202458991-ASHUT" — the tail after the UPI reference is the payee. */
    private val KOTAK_UPI_TAIL = Regex("""^UPI-\d+-(.+)$""", IGNORE_CASE)

    /**
     * A bank/UPI reference number. When present it is the perfect dedup key: the same debit
     * arriving as an SMS *and* as a UPI-app notification carries the same reference.
     */
    private val REFERENCE = Regex(
        """(?:upi(?:\s*ref(?:\s*no)?)?|ref(?:erence)?(?:\s*(?:no|id|num))?|txn(?:\s*(?:id|no))?|transaction\s*id)\s*[:.#\s-]*\s*([0-9]{6,22})""",
        IGNORE_CASE,
    )

    /** Ordered most-specific-first; the first pattern that hits wins. */
    private val MERCHANT_PATTERNS = listOf(
        Regex("""\bvpa\s+([a-z0-9._-]{2,}@[a-z]{2,})""", IGNORE_CASE),
        Regex("""\btrf\s+to\s+([a-z0-9][a-z0-9 &._'()-]{1,28})""", IGNORE_CASE),
        Regex("""\b(?:paid|sent)\s+to\s+([a-z0-9][a-z0-9 &._'()-]{1,28})""", IGNORE_CASE),
        Regex("""\bto\s+([A-Z][A-Za-z0-9 &._'()-]{2,28})""", setOf()),
        Regex("""\bat\s+([A-Za-z0-9][A-Za-z0-9 &._'()-]{2,28})""", IGNORE_CASE),
        Regex("""\b(?:info|towards|for)\s*[:\-]?\s*([A-Za-z0-9][A-Za-z0-9 &._'()-]{2,28})""", IGNORE_CASE),
    )

    /**
     * Leading junk. Email bodies wrap the payee in wording the generic patterns cannot see past
     * ("Payment Amount ₹232.0 Swiggy Food APL"), and a merchant called "Amount Swiggy Food APL"
     * then fails to match the same payee read from an SMS.
     */
    private val MERCHANT_LEAD = Regex(
        """^(?:amount|total|payment|paid|using|order|your|the|of|to|for|rs\.?|inr|₹|[0-9.,]+)\s+""",
        IGNORE_CASE,
    )

    /** Trailing junk that regularly gets swept into the merchant capture. */
    private val MERCHANT_TAIL = Regex(
        """\s*\b(on|ref|refno|upi|avl|available|bal|balance|a/c|ac|acct|not\s+you|if\s+not)\b.*$""",
        IGNORE_CASE,
    )

    /**
     * Settling a credit-card bill is not new spending — those purchases were already recorded the
     * day they happened. Anything landing in this category stays visible in the list but is left
     * out of the daily total, so a ₹1,500 CRED payment does not eat a ₹200 daily budget twice.
     */
    private val CARD_BILL_HINTS = listOf(
        "cred club", "cred.club", "cred club upi", "bbps", "billdesk", "card payment",
        "cc payment", "credit card bill", "card bill",
    )

    private val CATEGORY_KEYWORDS: List<Pair<ExpenseCategory, List<String>>> = listOf(
        ExpenseCategory.FOOD to listOf(
            "swiggy", "zomato", "dominos", "pizza", "mcdonald", "kfc", "burger", "cafe", "coffee",
            "restaurant", "dhaba", "bakery", "juice", "chai", "tea stall", "canteen", "mess",
            "biryani", "food", "namkeen", "hot dog", "sweets", "mithai", "samosa", "chaat",
            "ice cream", "eggs", "egg", "point",
        ),
        ExpenseCategory.GROCERIES to listOf(
            "blinkit", "zepto", "instamart", "bigbasket", "dmart", "grofers", "kirana", "grocery",
            "supermarket", "reliance fresh", "more retail", "vegetable", "milk", "dairy", "sanchi",
            "bazar", "bazaar", "aata", "atta", "provision", "general store",
        ),
        ExpenseCategory.TRANSPORT to listOf(
            "uber", "ola", "rapido", "irctc", "railway", "metro", "petrol", "fuel", "hpcl", "iocl",
            "bpcl", "indian oil", "redbus", "toll", "fastag", "parking", "auto",
        ),
        ExpenseCategory.STUDY to listOf(
            "made easy", "ace academy", "unacademy", "physics wallah", "coaching", "book",
            "stationery", "xerox", "photocopy", "print", "library", "course", "udemy", "coursera",
            "application fee",
        ),
        ExpenseCategory.BILLS to listOf(
            "recharge", "jio", "airtel", "vodafone", "bsnl", "electricity", "broadband", "wifi",
            "dth", "gas", "water bill", "rent", "netflix", "spotify", "subscription", "google",
        ),
        ExpenseCategory.HEALTH to listOf(
            "pharmacy", "apollo", "medplus", "1mg", "pharmeasy", "hospital", "clinic", "doctor",
            "medical", "lab", "diagnostic", "gym",
        ),
        ExpenseCategory.SHOPPING to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon", "mall",
        ),
    )

    data class Parsed(
        val amountPaise: Long,
        val merchant: String?,
        val category: ExpenseCategory,
        val fingerprint: String,
    )

    fun parse(text: String, occurredAt: Long, source: ExpenseSource): Expense? {
        val parsed = parseText(text, occurredAt) ?: return null
        return Expense(
            dayKey = dayKeyOf(occurredAt),
            amountPaise = parsed.amountPaise,
            merchant = parsed.merchant,
            category = parsed.category.id,
            occurredAt = occurredAt,
            source = source.id,
            rawText = text.take(400),
            fingerprint = parsed.fingerprint,
        )
    }

    fun parseText(text: String, occurredAt: Long): Parsed? {
        val flat = text.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()
        if (flat.isEmpty()) return null
        if (NOT_A_TRANSACTION.containsMatchIn(flat)) return null
        if (!DEBIT.containsMatchIn(flat)) return null

        // "credited" and "debited" can both appear when a message reports both sides of a
        // transfer; only treat it as spend if the debit verb comes first.
        val debitAt = DEBIT.find(flat)?.range?.first ?: return null
        val creditAt = CREDIT.find(flat)?.range?.first
        if (creditAt != null && creditAt < debitAt) return null

        val amountPaise = extractAmount(flat) ?: return null
        if (amountPaise <= 0) return null

        val axisUpi = AXIS_UPI.find(flat)
        val merchant = extractMerchant(flat, axisUpi)
        val reference = axisUpi?.groupValues?.get(2) ?: REFERENCE.find(flat)?.groupValues?.get(1)

        // With a bank reference the key is exact. Without one, fall back to amount + merchant
        // inside a two-minute window, which collapses the SMS/notification pair without merging
        // two genuinely separate payments.
        val fingerprint = if (reference != null) {
            "ref:$reference:$amountPaise"
        } else {
            val bucket = occurredAt / 120_000L
            "amt:$amountPaise:${merchant?.lowercase()?.replace(" ", "") ?: "?"}:$bucket"
        }

        // P2A is a transfer to a person's account, P2M a payment to a merchant. Keyword matching
        // on a person's name produces nonsense ("KANCHAN DEV" is not a purchase), so a P2A with no
        // merchant signal is left as Other rather than forced into a category.
        val personToPerson = axisUpi?.groupValues?.get(1).equals("P2A", ignoreCase = true)

        return Parsed(
            amountPaise = amountPaise,
            merchant = merchant,
            category = categorise(flat, merchant, personToPerson),
            fingerprint = fingerprint,
        )
    }

    private fun extractAmount(text: String): Long? {
        val raw = AMOUNT.find(text)?.groupValues?.get(1)
            ?: AMOUNT_TRAILING.find(text)?.groupValues?.get(1)
            ?: return null
        return runCatching {
            BigDecimal(raw.replace(",", ""))
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
        }.getOrNull()
    }

    private fun extractMerchant(text: String, axisUpi: MatchResult?): String? {
        axisUpi?.groupValues?.get(3)?.let { return clean(it) }
        AXIS_CARD.find(text)?.groupValues?.get(1)?.let { return clean(it) }

        for (pattern in MERCHANT_PATTERNS) {
            val hit = pattern.find(text)?.groupValues?.get(1) ?: continue
            val cleaned = clean(hit) ?: continue
            return cleaned
        }
        return null
    }

    private fun clean(raw: String): String? {
        // Kotak writes the payee after the UPI reference ("UPI-054202458991-ASHUT"), and this has
        // to happen before the tail strip — which would otherwise delete the whole string at "UPI".
        var value = raw.trim()
        KOTAK_UPI_TAIL.find(value)?.groupValues?.get(1)?.let { value = it.trim() }
        value = value.replace(MERCHANT_TAIL, "").trim().trim('.', ',', '-', '*', '/')
        var previous: String
        do {
            previous = value
            value = value.replace(MERCHANT_LEAD, "").trim()
        } while (value != previous)
        if (value.length < 3) return null
        // An account-number fragment ("XX1234") is not a merchant name.
        if (value.all { it.isDigit() || it == 'X' || it == 'x' || it == '*' }) return null
        return value.take(40)
    }

    fun categorise(
        text: String,
        merchant: String?,
        personToPerson: Boolean = false,
    ): ExpenseCategory {
        // Matched against the *payee* only, never the whole message: a Kotak card purchase says
        // "spent on Kotak Credit Card", and treating that as a bill settlement would quietly drop
        // real credit-card spending out of the daily total.
        val payee = (merchant ?: "").lowercase()
        if (CARD_BILL_HINTS.any { payee.contains(it) }) return ExpenseCategory.CARD_BILL

        val haystack = (payee + " " + text).lowercase()
        if (personToPerson) return ExpenseCategory.OTHER
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return ExpenseCategory.OTHER
    }
}
