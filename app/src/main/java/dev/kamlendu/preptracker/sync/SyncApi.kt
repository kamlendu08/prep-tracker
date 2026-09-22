package dev.kamlendu.preptracker.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * The two calls this app makes to the GATE platform.
 *
 * Written against `HttpURLConnection` and `org.json`, both in the platform, rather than pulling in
 * a networking stack and a serialiser for two endpoints and six fields. The payload shapes are
 * small and fixed, so the cost of a hand-written mapping is lower than the cost of the dependency.
 */
object SyncApi {

    /** The deployed GATE platform. Overridable in Settings for testing against a local server. */
    const val DEFAULT_BASE_URL = "https://gatexprep.vercel.app"

    private const val TIMEOUT_MS = 20_000

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        /** [signedOut] means the token was rejected — the only error that should sign the user out. */
        data class Failed(val message: String, val signedOut: Boolean = false) : Result<Nothing>
    }

    data class Session(
        val uid: String,
        val dayKey: String,
        val activity: String,
        val startedAt: Long,
        val endedAt: Long,
        val durationMs: Long,
        val manual: Boolean,
        val note: String?,
        val deleted: Boolean,
        val updatedAt: Long,
    )

    data class Spend(
        val uid: String,
        val dayKey: String,
        val amountPaise: Long,
        val merchant: String?,
        val category: String,
        val occurredAt: Long,
        val source: String,
        val fingerprint: String,
        val categoryLocked: Boolean,
        val deleted: Boolean,
        val updatedAt: Long,
    )

    /**
     * A note on a revision card. The card's slug path is the id on both sides, so the same note
     * written on the website and on the phone is one row rather than two.
     */
    data class Remark(
        val cardId: String,
        val text: String,
        val deleted: Boolean,
        val updatedAt: Long,
    )

    data class SyncResponse(
        val serverTime: Long,
        val sessions: List<Session>,
        val spends: List<Spend>,
        /**
         * **null means the server did not send the key at all** — it is older than this app and
         * knows nothing about revision remarks. That is not the same as an empty list, and the
         * difference matters: an unknown key is silently dropped on the way in, so the request
         * still returns 200 and the phone would otherwise mark the note synced and never send it
         * again. Distinguishing the two is what stops a note being lost during a staged rollout.
         */
        val revisionRemarks: List<Remark>?,
    )

    data class LoginResponse(val token: String, val userId: String, val email: String, val name: String?)

    suspend fun login(baseUrl: String, email: String, password: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("email", email).put("password", password)
            readLogin(post("$baseUrl/api/mobile/login", null, body))
        }

    /** Sign-in and registration return the same shape — an account and a token to use it with. */
    private fun readLogin(response: Result<JSONObject>): Result<LoginResponse> = when (response) {
        is Result.Failed -> response
        is Result.Ok -> {
            val json = response.value
            val user = json.getJSONObject("user")
            Result.Ok(
                LoginResponse(
                    token = json.getString("token"),
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    name = user.optString("name").takeIf { it.isNotBlank() && it != "null" },
                )
            )
        }
    }

    suspend fun register(
        baseUrl: String,
        name: String,
        email: String,
        password: String,
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("name", name).put("email", email).put("password", password)
        readLogin(post("$baseUrl/api/mobile/register", null, body))
    }

    suspend fun sync(
        baseUrl: String,
        token: String,
        since: Long,
        sessions: List<Session>,
        spends: List<Spend>,
        remarks: List<Remark>,
    ): Result<SyncResponse> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("since", if (since > 0) since else JSONObject.NULL)
            .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })
            .put("spends", JSONArray().apply { spends.forEach { put(it.toJson()) } })
            .put("revisionRemarks", JSONArray().apply { remarks.forEach { put(it.toJson()) } })

        when (val response = post("$baseUrl/api/mobile/sync", token, body)) {
            is Result.Failed -> response
            is Result.Ok -> {
                val json = response.value
                Result.Ok(
                    SyncResponse(
                        serverTime = json.optLong("serverTime", System.currentTimeMillis()),
                        sessions = json.getJSONArray("sessions").map { it.toSession() },
                        spends = json.getJSONArray("spends").map { it.toSpend() },
                        // Kept null when the key is absent — see the field's comment.
                        revisionRemarks = json.optJSONArray("revisionRemarks")?.map { it.toRemark() },
                    )
                )
            }
        }
    }

    private fun post(url: String, token: String?, body: JSONObject): Result<JSONObject> {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            when {
                code in 200..299 -> Result.Ok(JSONObject(text))
                code == 401 -> Result.Failed("Signed out — sign in again.", signedOut = true)
                else -> Result.Failed(
                    runCatching { JSONObject(text).optString("error") }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "Server error ($code)."
                )
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "Could not reach the server.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun Session.toJson() = JSONObject()
        .put("id", uid)
        .put("dayKey", dayKey)
        .put("activity", activity)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt)
        .put("durationMs", durationMs)
        .put("manual", manual)
        .put("note", note ?: JSONObject.NULL)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)

    private fun Spend.toJson() = JSONObject()
        .put("id", uid)
        .put("dayKey", dayKey)
        .put("amountPaise", amountPaise)
        .put("merchant", merchant ?: JSONObject.NULL)
        .put("category", category)
        .put("occurredAt", occurredAt)
        .put("source", source)
        .put("fingerprint", fingerprint)
        .put("categoryLocked", categoryLocked)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)

    private fun Remark.toJson() = JSONObject()
        .put("cardId", cardId)
        .put("text", text)
        .put("deleted", deleted)
        .put("updatedAt", updatedAt)

    private fun JSONObject.toRemark() = Remark(
        cardId = getString("cardId"),
        text = optString("text"),
        deleted = optBoolean("deleted"),
        updatedAt = getLong("updatedAt"),
    )

    private fun JSONObject.toSession() = Session(
        uid = getString("id"),
        dayKey = getString("dayKey"),
        activity = getString("activity"),
        startedAt = getLong("startedAt"),
        endedAt = getLong("endedAt"),
        durationMs = getLong("durationMs"),
        manual = optBoolean("manual"),
        note = if (isNull("note")) null else optString("note"),
        deleted = optBoolean("deleted"),
        updatedAt = getLong("updatedAt"),
    )

    private fun JSONObject.toSpend() = Spend(
        uid = getString("id"),
        dayKey = getString("dayKey"),
        amountPaise = getLong("amountPaise"),
        merchant = if (isNull("merchant")) null else optString("merchant"),
        category = getString("category"),
        occurredAt = getLong("occurredAt"),
        source = getString("source"),
        fingerprint = getString("fingerprint"),
        categoryLocked = optBoolean("categoryLocked"),
        deleted = optBoolean("deleted"),
        updatedAt = getLong("updatedAt"),
    )

    private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }
}
