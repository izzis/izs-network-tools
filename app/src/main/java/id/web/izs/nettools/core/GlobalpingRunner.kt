package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Global ping/trace via the free Globalping API (api.globalping.io).
 *  No key needed (250 tests/hour, max 50 probes); an optional dashboard
 *  token raises the limit. Empty locations[] = probes spread worldwide. */
object GlobalpingRunner {

    private const val BASE = "https://api.globalping.io/v1/measurements"
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun ping(
        target: String,
        probes: Int,
        country: String,
        token: String,
        onProgress: (String) -> Unit = {}
    ): Flow<String> = flow {
        runMeasure("ping", target, probes, country, token, onProgress, ::formatPing)
    }.flowOn(Dispatchers.IO)

    fun trace(
        target: String,
        probes: Int,
        country: String,
        token: String,
        onProgress: (String) -> Unit = {}
    ): Flow<String> = flow {
        runMeasure("traceroute", target, probes, country, token, onProgress, ::formatTrace)
    }.flowOn(Dispatchers.IO)

    /** Live-update marker: lines starting with this char are "key\\ntext".
     *  A later line with the same key REPLACES the earlier one in place
     *  (used for in-progress placeholders). Never shown; stripped for display. */
    const val LIVE = "\u001E"

    /** Remove marker: `REMOVE + bssid` deletes the stored LIVE line keyed by
     *  that bssid (WiFi `(gone)` rows auto-dropped after one refresh cycle).
     *  Consumed in the ViewModel — never stored, never shown. */
    const val REMOVE = "\u001D"

    fun displayOf(line: String): String =
        if (line.startsWith(LIVE)) line.substringAfter('\n') else line

    // NOTE: no withContext() in here — the flow already runs on
    // Dispatchers.IO via flowOn, and emit() from another context violates
    // Flow exception transparency (same rule as IpInfoClient).
    private suspend fun FlowCollector<String>.runMeasure(
        type: String,
        target: String,
        probes: Int,
        country: String,
        token: String,
        onProgress: (String) -> Unit,
        format: (JSONObject?, JSONObject?) -> String?
    ) {
        val where = if (country.isEmpty()) "$probes probes worldwide" else "$probes probe(s) from $country"
        emit(";; global $type for $target via Globalping ($where)")
        val id = postCreate(type, target, probes, country, token) ?: return
        // The run only ends when every probe result is final: in-progress
        // entries stay on screen as placeholders and are replaced in place
        // ("key\ntext" lines) as results stream in behind the scenes.
        val runTag = System.currentTimeMillis().toString(36)
        val live = mutableSetOf<Int>()
        val settled = mutableSetOf<Int>()
        var done = false
        repeat(120) {
            delay(1000)
            val m = getMeasure(id, token) ?: return
            val results = m.optJSONArray("results") ?: JSONArray()
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val key = "$LIVE$runTag:$i"
                val res = r.optJSONObject("result")
                // Two status levels: the entry's and the inner result's. Either
                // can lag as "in-progress" — only settle when both are final,
                // so the literal string "in-progress" can never reach output.
                if (r.optString("status") == "in-progress" ||
                    res?.optString("status") == "in-progress"
                ) {
                    if (live.add(i)) emit("$key\n${probeLabel(r.optJSONObject("probe"))}: …")
                    continue
                }
                if (settled.add(i)) {
                    emit("$key\n${format(r.optJSONObject("probe"), res) ?: "no result"}")
                }
            }
            onProgress("Global: ${settled.size}/$probes probes")
            val pending = (0 until results.length())
                .any { k ->
                    val rk = results.optJSONObject(k)
                    rk?.optString("status") == "in-progress" ||
                        rk?.optJSONObject("result")?.optString("status") == "in-progress"
                }
            if (m.optString("status") == "finished" && !pending) {
                done = true
                return
            }
        }
        emit(if (done) ";; done (${settled.size} probes)" else ";; timed out waiting for probes (${settled.size} received)")
    }

    /** POST a measurement; emits the API error and returns null on failure. */
    private suspend fun FlowCollector<String>.postCreate(
        type: String,
        target: String,
        probes: Int,
        country: String,
        token: String
    ): String? {
        val locations = JSONArray().apply {
            if (country.isNotBlank()) put(JSONObject().put("country", country.trim().uppercase()))
        }
        val body = JSONObject()
            .put("type", type)
            .put("target", target)
            .put("limit", probes.coerceIn(1, 50))
            .put("locations", locations)
            .put("measurementOptions", JSONObject().put("packets", 3))
            .toString()
            .toRequestBody(JSON_TYPE)
        val req = Request.Builder()
            .url(BASE)
            .post(body)
            .header("User-Agent", "IZS-Network-Tools/1.0")
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
            .build()
        val raw = try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    emit(";; Globalping HTTP ${resp.code}: ${apiError(text)}")
                    return null
                }
                text
            }
        } catch (e: Exception) {
            emit(";; failed: ${e.message}")
            return null
        }
        return try {
            JSONObject(raw).optString("id").ifEmpty { null }
        } catch (_: Exception) {
            emit(";; bad create response: ${raw.take(200)}")
            null
        }
    }

    /** GET one measurement; emits the error and returns null on failure. */
    private suspend fun FlowCollector<String>.getMeasure(id: String, token: String): JSONObject? {
        val req = Request.Builder()
            .url("$BASE/$id")
            .header("User-Agent", "IZS-Network-Tools/1.0")
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body.string()
                if (!resp.isSuccessful) {
                    emit(";; poll HTTP ${resp.code}: ${apiError(text)}")
                    return null
                }
                JSONObject(text)
            }
        } catch (e: Exception) {
            emit(";; poll failed: ${e.message}")
            null
        }
    }

    private fun apiError(text: String): String {
        if (text.isBlank()) return "no detail"
        return try {
            val o = JSONObject(text)
            o.optString("message").ifEmpty {
                o.optJSONObject("error")?.optString("message") ?: text.take(200)
            }
        } catch (_: Exception) {
            text.take(200)
        }
    }

    private fun probeLabel(p: JSONObject?): String {
        if (p == null) return "??"
        val cc = p.optString("country", "").ifEmpty { "??" }
        val city = p.optString("city", "")
        val asn = p.optInt("asn", 0).let { if (it > 0) " AS$it" else "" }
        return "$cc${if (city.isNotEmpty()) " $city" else ""}$asn"
    }

    private fun formatPing(probe: JSONObject?, res: JSONObject?): String? {
        if (res == null) return null
        // Safety net: "in-progress" must never surface as text (see runMeasure).
        if (res.optString("status") == "in-progress") return "${probeLabel(probe)}: …"
        if (res.optString("status") != "finished") return "${probeLabel(probe)}: ${res.optString("status")}"
        val st = res.optJSONObject("stats")
        return if (st != null) {
            val loss = st.optDouble("loss", Double.NaN)
            "${probeLabel(probe)}: avg=${fmtMs(st.optDouble("avg"))} " +
                "min=${fmtMs(st.optDouble("min"))} max=${fmtMs(st.optDouble("max"))} " +
                "loss=${if (loss.isNaN()) "?" else "${loss.toInt()}%"}"
        } else {
            // Fallback: first + last raw lines only, full output is too noisy.
            val raw = res.optString("rawOutput", "").lines().filter { it.isNotBlank() }
            "${probeLabel(probe)}: ${(raw.firstOrNull() ?: "no stats").take(120)}"
        }
    }

    private fun formatTrace(probe: JSONObject?, res: JSONObject?): String? {
        if (res == null) return null
        // Safety net: "in-progress" must never surface as text (see runMeasure).
        if (res.optString("status") == "in-progress") return "${probeLabel(probe)}: …"
        if (res.optString("status") != "finished") return "${probeLabel(probe)}: ${res.optString("status")}"
        val hops = res.optJSONArray("hops") ?: return "${probeLabel(probe)}: no hops"
        val sb = StringBuilder(probeLabel(probe)).append(':')
        for (i in 0 until hops.length()) {
            val h = hops.optJSONObject(i) ?: continue
            val host = h.optString("resolvedAddress").ifEmpty { h.optString("resolvedHostname", "*") }
            val rtts = h.optJSONArray("timings")?.let { t ->
                (0 until t.length()).mapNotNull { k ->
                    t.optJSONObject(k)?.optDouble("rtt", Double.NaN)?.takeUnless { it.isNaN() }?.let(::fmtMs)
                }
            } ?: emptyList()
            sb.append("\n  ${i + 1} ${host.ifEmpty { "*" }}${if (rtts.isNotEmpty()) " " + rtts.joinToString(" ") else ""}")
        }
        return sb.toString().trimEnd().take(1500)
    }

    private fun fmtMs(v: Double): String =
        if (v.isNaN()) "?" else if (v >= 100) "${v.toInt()}ms" else "${"%.1f".format(v)}ms"
}
