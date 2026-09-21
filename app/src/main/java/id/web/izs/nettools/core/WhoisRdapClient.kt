package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * RDAP-first, WHOIS fallback. All servers configurable, no API key.
 * Domain: IANA RDAP bootstrap -> authoritative RDAP, fallback rdap.org, fallback port 43.
 * IP: rdap.org/ip, fallback whois.iana.org referral.
 */
object WhoisRdapClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "IZS-Network-Tools/1.0")
                .header("Accept", "application/rdap+json, application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun whoisQuery(server: String, port: Int, query: String, timeoutMs: Int): String {
        return try {
            Socket().use { sock ->
                sock.soTimeout = timeoutMs
                sock.connect(java.net.InetSocketAddress(server, port), timeoutMs)
                sock.getOutputStream().write("$query\r\n".toByteArray())
                sock.getOutputStream().flush()
                sock.getInputStream().bufferedReader().readText()
            }
        } catch (e: Exception) {
            ";; whois to $server failed: ${e.message}"
        }
    }

    private fun findReferral(text: String): String? {
        // IANA / RIR style referrals
        for (line in text.lines()) {
            val t = line.trim()
            if (t.startsWith("whois:", ignoreCase = true)) {
                t.substringAfter(":").trim().split(Regex("\\s+")).firstOrNull()?.let {
                    if (it.contains('.')) return it
                }
            }
            if (t.startsWith("refer:", ignoreCase = true)) {
                val v = t.substringAfter(":").trim().split(Regex("\\s+")).firstOrNull()
                if (v != null && v.contains('.') && !v.startsWith("whois://").not()) return v.removePrefix("whois://")
                if (v != null && v.contains('.')) return v
            }
            if (t.startsWith("Registrar WHOIS Server:", ignoreCase = true)) {
                val v = t.substringAfter(":").trim()
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }

    private fun prettyRdap(json: String): String {
        return try {
            val o = JSONObject(json)
            val sb = StringBuilder()
            fun put(k: String, v: Any?) {
                if (v != null && v.toString().isNotEmpty() && v.toString() != "null") {
                    sb.appendLine("$k: $v")
                }
            }
            put("Name", o.optString("ldhName", o.optString("name", "")))
            put("Handle", o.optString("handle", ""))
            put("Status", o.optJSONArray("status")?.join(", "))
            o.optJSONArray("events")?.let { ev ->
                for (i in 0 until ev.length()) {
                    val e = ev.optJSONObject(i) ?: continue
                    sb.appendLine("${e.optString("eventAction")}: ${e.optString("eventDate")}")
                }
            }
            o.optJSONArray("entities")?.let { ents ->
                for (i in 0 until ents.length()) {
                    val e = ents.optJSONObject(i) ?: continue
                    val roles = e.optJSONArray("roles")?.join(", ")
                    sb.appendLine("Entity[$roles]: ${e.optString("handle")}")
                    e.optJSONArray("vcardArray")?.optJSONArray(1)?.let { vc ->
                        for (j in 0 until vc.length()) {
                            val f = vc.optJSONArray(j) ?: continue
                            if (f.length() >= 4) sb.appendLine("  ${f.optString(0)}: ${f.opt(3)}")
                        }
                    }
                }
            }
            o.optJSONArray("nameservers")?.let { ns ->
                val list = (0 until ns.length()).mapNotNull { ns.optJSONObject(it)?.optString("ldhName") }
                if (list.isNotEmpty()) sb.appendLine("Nameservers: ${list.joinToString(", ")}")
            }
            o.optString("port43", "").ifEmpty { null }?.let { put("WHOIS port43", it) }
            if (sb.isEmpty()) json.take(4000) else sb.toString().trimEnd()
        } catch (_: Exception) {
            json.take(4000)
        }
    }

    fun lookup(
        target: String,
        rdapBase: String,
        whoisServer: String,
        whoisPort: Int,
        timeoutMs: Int
    ): Flow<String> = flow {
        val isIp = TargetParser.isIp(target)
        emit(";; whois/rdap for $target\n")
        if (isIp) {
            val rdap = httpGet("${rdapBase.trimEnd('/')}/ip/$target")
            if (rdap != null) {
                emit("== RDAP (via $rdapBase) ==")
                emit(prettyRdap(rdap))
                return@flow
            }
            emit(";; RDAP failed, falling back to WHOIS port 43")
            val first = whoisQuery(whoisServer, whoisPort, target, timeoutMs)
            emit(first.take(6000))
            val ref = findReferral(first)
            if (ref != null && !ref.equals(whoisServer, ignoreCase = true)) {
                emit("\n;; following referral to $ref\n")
                emit(whoisQuery(ref, 43, target, timeoutMs).take(8000))
            }
        } else {
            val domain = target.lowercase().trim()
            // 1) IANA bootstrap for authoritative RDAP
            var rdapUrl: String? = null
            val tld = domain.substringAfterLast('.', "")
            if (tld.isNotEmpty()) {
                val boot = httpGet("https://data.iana.org/rdap/dns.json")
                if (boot != null) {
                    try {
                        val services = JSONObject(boot).optJSONArray("services")
                        if (services != null) {
                            outer@ for (i in 0 until services.length()) {
                                val entry = services.optJSONArray(i) ?: continue
                                val tlds = entry.optJSONArray(0)
                                val urls = entry.optJSONArray(1)
                                if (tlds != null && urls != null) {
                                    for (j in 0 until tlds.length()) {
                                        if (tlds.optString(j).equals(tld, ignoreCase = true)) {
                                            rdapUrl = urls.optString(0).trimEnd('/') + "/domain/" + domain
                                            break@outer
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            rdapUrl = rdapUrl ?: "${rdapBase.trimEnd('/')}/domain/$domain"
            val rdap = httpGet(rdapUrl)
            if (rdap != null && !rdap.contains("\"errorCode\"")) {
                emit("== RDAP ($rdapUrl) ==")
                emit(prettyRdap(rdap))
                return@flow
            }
            emit(";; RDAP unavailable, falling back to WHOIS port 43")
            val tldWhois = "whois.nic.$tld"
            val serversToTry = listOf(tldWhois, whoisServer).distinct()
            var raw = ""
            for (srv in serversToTry) {
                raw = whoisQuery(srv, if (srv == whoisServer) whoisPort else 43, domain, timeoutMs)
                if (!raw.startsWith(";; whois to")) break
                emit(raw)
            }
            if (!raw.startsWith(";; whois to")) {
                emit(raw.take(6000))
                val ref = findReferral(raw)
                if (ref != null) {
                    emit("\n;; following referral to $ref\n")
                    emit(whoisQuery(ref, 43, domain, timeoutMs).take(8000))
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
