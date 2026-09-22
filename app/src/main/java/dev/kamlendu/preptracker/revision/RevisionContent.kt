package dev.kamlendu.preptracker.revision

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The revision section's content, bundled with the app.
 *
 * It ships as assets rather than coming down from the server for one reason: the whole point is
 * revising away from a desk, which includes a train, a queue, and anywhere the signal is bad. The
 * files are generated from the website's own `data/revision` JSON by
 * `scripts/build-revision-assets.mjs` there, with the maths already typeset to HTML — see
 * `docs/revision.md` for how to regenerate them after editing content.
 *
 * Nothing here is user data. The candidate's own notes live in Room (`revision_remarks`).
 */
data class SubjectSummary(
    val slug: String,
    val subject: String,
    /** Section number in the official GATE EC syllabus; null for General Aptitude. */
    val section: Int?,
    val sharePct: Double,
    val perPaper: Double,
    val pyqQuestions: Int,
    val topics: List<TopicSummary>,
) {
    val cardCount: Int get() = topics.sumOf { it.cards }
}

data class TopicSummary(val slug: String, val title: String, val cards: Int)

data class Subject(
    val slug: String,
    val subject: String,
    val section: Int?,
    val sharePct: Double,
    val perPaper: Double,
    val topics: List<Topic>,
)

data class Topic(
    val slug: String,
    val title: String,
    /** Verbatim from the official syllabus, so the scope shown is never a guess. */
    val syllabus: String,
    val cards: List<Card>,
)

data class Card(
    val slug: String,
    /** Plain text — used outside the WebView, where HTML would show as markup. */
    val title: String,
    val titleHtml: String,
    val bodyHtml: String,
    val pitfallHtml: String?,
    val formulas: List<Formula>,
)

data class Formula(val exprHtml: String, val noteHtml: String?)

object RevisionContent {

    /** `subject/topic/card` — the same identity the website's remarks use. */
    fun cardId(subjectSlug: String, topicSlug: String, cardSlug: String) =
        "$subjectSlug/$topicSlug/$cardSlug"

    @Volatile
    private var index: List<SubjectSummary>? = null
    private val subjects = HashMap<String, Subject>()

    suspend fun index(context: Context): List<SubjectSummary> {
        index?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching {
                readArray(context, "revision/index.json").map { it.toSummary() }
            }.getOrDefault(emptyList())
            index = parsed
            parsed
        }
    }

    suspend fun subject(context: Context, slug: String): Subject? {
        synchronized(subjects) { subjects[slug] }?.let { return it }
        return withContext(Dispatchers.IO) {
            val parsed = runCatching { readObject(context, "revision/$slug.json").toSubject() }
                .getOrNull()
            if (parsed != null) synchronized(subjects) { subjects[slug] = parsed }
            parsed
        }
    }

    suspend fun topic(context: Context, subjectSlug: String, topicSlug: String): Pair<Subject, Topic>? {
        val s = subject(context, subjectSlug) ?: return null
        val t = s.topics.firstOrNull { it.slug == topicSlug } ?: return null
        return s to t
    }

    private fun readText(context: Context, path: String) =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun readArray(context: Context, path: String) = JSONArray(readText(context, path))

    private fun readObject(context: Context, path: String) = JSONObject(readText(context, path))

    private fun JSONObject.section(): Int? = if (isNull("section")) null else optInt("section")

    private fun JSONObject.toSummary(): SubjectSummary {
        val pyq = getJSONObject("pyq")
        return SubjectSummary(
            slug = getString("slug"),
            subject = getString("subject"),
            section = section(),
            sharePct = pyq.optDouble("sharePct", 0.0),
            perPaper = pyq.optDouble("perPaper", 0.0),
            pyqQuestions = pyq.optInt("questions", 0),
            topics = getJSONArray("topics").map {
                TopicSummary(
                    slug = it.getString("slug"),
                    title = it.getString("title"),
                    cards = it.optInt("cards", 0),
                )
            },
        )
    }

    private fun JSONObject.toSubject(): Subject {
        val pyq = getJSONObject("pyq")
        return Subject(
            slug = getString("slug"),
            subject = getString("subject"),
            section = section(),
            sharePct = pyq.optDouble("sharePct", 0.0),
            perPaper = pyq.optDouble("perPaper", 0.0),
            topics = getJSONArray("topics").map { t ->
                Topic(
                    slug = t.getString("slug"),
                    title = t.getString("title"),
                    syllabus = t.optString("syllabus"),
                    cards = t.getJSONArray("cards").map { c ->
                        Card(
                            slug = c.getString("slug"),
                            title = c.optString("title"),
                            titleHtml = c.optString("titleHtml"),
                            bodyHtml = c.optString("bodyHtml"),
                            pitfallHtml = if (c.isNull("pitfallHtml")) null else c.optString("pitfallHtml"),
                            formulas = c.optJSONArray("formulas")?.map { f ->
                                Formula(
                                    exprHtml = f.optString("exprHtml"),
                                    noteHtml = if (f.isNull("noteHtml")) null else f.optString("noteHtml"),
                                )
                            }.orEmpty(),
                        )
                    },
                )
            },
        )
    }

    private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
