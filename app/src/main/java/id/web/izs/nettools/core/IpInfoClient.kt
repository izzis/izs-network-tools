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
        try {
            val o = JSONObject(body)
            val sb = StringBuilder()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.opt(k)
                if (v == null || v == JSONObject.NULL) continue
                if (v is JSONObject || v is org.json.JSONArray) {
                    sb.appendLine("$k: ${v.toString().take(300)}")
                } else {
                    sb.appendLine("$k: $v")
                }
            }
            emit(if (sb.isEmpty()) body.take(4000) else sb.toString().trimEnd())
        } catch (_: Exception) {
            emit(body.take(4000))
        }
    }.flowOn(Dispatchers.IO)
}
