package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Passive "global" port lookup via Shodan InternetDB.
 *
 * Free for non-commercial use, no API key, one GET per lookup:
 *   GET https://internetdb.shodan.io/{ip}
 * 200 -> {"ip","ports":[..],"vulns":[..],"hostnames":[..],"cpes":[..],"tags":[..]}
 * 404 -> {"detail":"No information available"} (IP never scanned by Shodan).
 *
 * Data is Shodan's weekly scan, NOT a live scan: good for a quick view of
 * what a host exposes to the public internet, plus known vulns. Hostnames
 * are resolved to an IP first (InternetDB is IP-only).
 */
object InternetDbClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun lookup(host: String): Flow<String> = flow {
        val raw = host.trim()
        if (raw.isEmpty()) {
            emit("ERROR: empty target")
            return@flow
        }
        val ip = try {
            InetAddress.getByName(raw).hostAddress ?: raw
        } catch (_: Exception) {
            emit("ERROR: cannot resolve $raw")
            return@flow
        }
        if (!ip.equals(raw, ignoreCase = true)) emit(";; resolved $raw -> $ip")
        val req = Request.Builder()
            .url("https://internetdb.shodan.io/$ip")
            .header("User-Agent", "IZS-Network-Tools/1.0")
            .build()
        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            emit("ERROR: internetdb request failed (${e.message})")
            return@flow
        }
        resp.use { r ->
            val body = r.body.string()
            if (r.code == 404) {
                emit(";; no data in InternetDB for $ip (not scanned yet)")
                return@flow
            }
            if (!r.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail", "") }.getOrDefault("")
                emit("ERROR: internetdb HTTP ${r.code}${if (detail.isNotEmpty()) " ($detail)" else ""}")
                return@flow
            }
            val o = runCatching { JSONObject(body) }.getOrNull()
            if (o == null) {
                emit("ERROR: internetdb returned invalid JSON")
                return@flow
            }
            val ports = o.optJSONArray("ports")?.let { a ->
                (0 until a.length()).mapNotNull { a.optInt(it, -1).takeIf { p -> p >= 0 } }.sorted()
            }.orEmpty()
            if (ports.isEmpty()) {
                emit(";; no open ports reported for $ip")
            } else {
                ports.forEach { p ->
                    emit(PortChecker.openLine(p, null, PortChecker.serviceByPort[p], confirmed = false))
                }
            }
            emit("")
            emit("Done: ${ports.size} open ports (passive lookup, not a live scan).")
            o.optJSONArray("hostnames")?.join(", ")?.let { if (it.isNotEmpty()) emit(";; hostnames: $it") }
            val vulns = o.optJSONArray("vulns")?.join(", ").orEmpty()
            emit(";; vulns: ${if (vulns.isNotEmpty()) vulns else "none reported"}")
            o.optJSONArray("tags")?.join(", ")?.let { if (it.isNotEmpty()) emit(";; tags: $it") }
            emit(";; source: Shodan InternetDB (weekly scan, free non-commercial use)")
        }
    }.flowOn(Dispatchers.IO)

    private fun org.json.JSONArray.join(sep: String): String =
        (0 until length()).map { optString(it, "") }.filter { it.isNotEmpty() }.joinToString(sep)
}
