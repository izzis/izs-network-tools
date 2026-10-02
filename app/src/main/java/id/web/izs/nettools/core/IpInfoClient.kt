package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Generic IP-info client: works with ipwho.is, ip-api.com, ipaddress.to (no key). Base URL configurable. */
object IpInfoClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** True when target is a hostname that must be DNS-resolved before the
     *  provider call: ipwho.is and ipinfo.io answer 404 for domains, so the
     *  app resolves first (ipwho.is-style: hostname -> IP, then lookup). */
    internal fun needsResolve(target: String, me: Boolean, selfOnly: Boolean): Boolean =
        !me && !selfOnly && target.isNotEmpty() && !TargetParser.isIp(target)

    /** Provider URL for a target that is already an IP literal (or me). */
    internal fun buildUrl(base: String, target: String, me: Boolean, selfOnly: Boolean): String = when {
        base.contains("{ip}") -> base.replace("{ip}", if (me) "my" else target)
        me && base.contains("api.ipinfo.io/lite") -> "$base/me"
        me && base.contains("ipaddress.to") -> "$base/my"
        me -> base
        // Some free providers only report the caller's own IP (no lookup for others).
        selfOnly -> base
        base.contains("ip-api.com") -> "$base/$target?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
        base.contains("ipaddress.to") -> "$base/$target"
        // Default ipinfo.io preset keeps "/json" in the base: the lookup
        // path is ipinfo.io/{ip}/json, not ipinfo.io/json/{ip} (404).
        base.equals("https://ipinfo.io/json", ignoreCase = true) -> "https://ipinfo.io/$target/json"
        else -> "$base/$target"
    }

    fun lookup(target: String, base: String, token: String = ""): Flow<String> = flow {
        val b = base.trim().trimEnd('/')
        val t = token.trim()
        val me = target.isEmpty()
        val selfOnly = b.contains("api.ipify.org") ||
            b.contains("icanhazip.com") ||
            b.contains("amazonaws.com")
        var q = target.trim()
        if (needsResolve(q, me, selfOnly)) {
            val ip = try {
                InetAddress.getByName(q).hostAddress
            } catch (e: Exception) {
                emit(";; DNS $q: ${e.message ?: "cannot resolve"}")
                return@flow
            }
            emit(";; $q -> $ip (DNS)")
            q = ip
        }
        val url = buildUrl(b, q, me, selfOnly).let {
            // Free ipinfo.io token (Lite plan): authenticates the request for
            // unlimited quota instead of the shared anonymous limit.
            if (t.isNotEmpty() && b.contains("ipinfo.io")) {
                if (it.contains("?")) "$it&token=$t" else "$it?token=$t"
            } else it
        }
        val head = buildString {
            if (me) append(";; public IP of this device\n")
            if (!me && selfOnly) append(";; note: this provider only reports your own IP - target ignored\n")
            append(";; GET $url\n")
        }
        emit(head)
        // NOTE: no withContext() here — this flow already runs on Dispatchers.IO
        // via flowOn, and emit() from another context violates Flow exception
        // transparency.
        val body = try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IZS-Network-Tools/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    emit(";; HTTP ${resp.code}: ${resp.message}")
                    return@flow
                }
                resp.body.string()
            }
        } catch (e: Exception) {
            emit(";; failed: ${e.message}")
            return@flow
        }
        emit(prettyJson(body) ?: body.take(4000))
    }.flowOn(Dispatchers.IO)

    private const val MAX_DEPTH = 6
    private const val MAX_CHARS = 4000

    /** YAML-ish rendering: nested objects/arrays indent 2 spaces, arrays use `- `
     *  items, nulls are dropped, top-level `is_*` booleans collapse into one
     *  `flags:` line. Null when the body is not a JSON object or renders empty. */
    internal fun prettyJson(body: String, maxChars: Int = MAX_CHARS): String? {
        val o = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val lines = objLines(o, 0, 0, topLevel = true)
        if (lines.isEmpty()) return null
        val text = lines.joinToString("\n")
        if (text.length <= maxChars) return text
        val cut = text.lastIndexOf('\n', maxChars)
        return (if (cut > 0) text.substring(0, cut) else text.take(maxChars)) + "\n… (truncated)"
    }

    private fun objLines(o: JSONObject, indent: Int, depth: Int, topLevel: Boolean): List<String> {
        val pad = " ".repeat(indent)
        val keys = ArrayList<String>()
        val it = o.keys()
        while (it.hasNext()) keys.add(it.next())
        val flagKeys = keys.filter { it.startsWith("is_") && o.opt(it) is Boolean }
        val out = ArrayList<String>(keys.size)
        var flagsDone = false
        for (k in keys) {
            val v = o.opt(k)
            if (v == null || v === JSONObject.NULL) continue
            if (topLevel && v is Boolean && k.startsWith("is_")) {
                if (!flagsDone) {
                    out.add(
                        pad + "flags: " + flagKeys.joinToString(" ") {
                            "${it.removePrefix("is_")}=${if (o.opt(it) == true) "yes" else "no"}"
                        }
                    )
                    flagsDone = true
                }
                continue
            }
            out.addAll(entryLines(k, v, pad, depth))
        }
        return out
    }

    private fun entryLines(k: String, v: Any, pad: String, depth: Int): List<String> = when (v) {
        is JSONObject -> when {
            depth >= MAX_DEPTH -> listOf("$pad$k: ${compact(v)}")
            v.length() == 0 -> listOf("$pad$k: {}")
            else -> listOf("$pad$k:") + objLines(v, pad.length + 2, depth + 1, false)
        }
        is JSONArray -> when {
            depth >= MAX_DEPTH -> listOf("$pad$k: ${compact(v)}")
            v.length() == 0 -> listOf("$pad$k: []")
            else -> listOf("$pad$k:") + arrLines(v, pad.length + 2, depth + 1)
        }
        else -> listOf("$pad$k: ${scalar(v)}")
    }

    private fun arrLines(a: JSONArray, indent: Int, depth: Int): List<String> {
        val pad = " ".repeat(indent)
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val v = a.opt(i)
            if (v == null || v === JSONObject.NULL) continue
            when {
                v is JSONObject && depth < MAX_DEPTH -> {
                    if (v.length() == 0) {
                        out.add("$pad- {}")
                    } else {
                        val sub = objLines(v, indent + 2, depth + 1, false)
                        out.add(pad + "- " + sub[0].removePrefix(pad + "  "))
                        out.addAll(sub.subList(1, sub.size))
                    }
                }
                v is JSONArray && depth < MAX_DEPTH -> {
                    out.add("$pad-")
                    out.addAll(arrLines(v, indent + 2, depth + 1))
                }
                v is JSONObject || v is JSONArray -> out.add("$pad- ${compact(v)}")
                else -> out.add("$pad- ${scalar(v)}")
            }
        }
        return out
    }

    private fun compact(v: Any): String = v.toString().take(200)

    private fun scalar(v: Any): String = when {
        v is Double || v is Float -> fmtDouble(v.toDouble())
        v is java.math.BigDecimal -> fmtDouble(v.toDouble())
        else -> v.toString()
    }

    /** Trim binary-float noise (`0.5166700000000001` -> `0.51667`). */
    private fun fmtDouble(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return d.toString()
        val s = String.format(java.util.Locale.US, "%.6f", d).trimEnd('0').trimEnd('.')
        return if ((s.isEmpty() || s == "0" || s == "-0") && d != 0.0) d.toString() else s
    }
}
