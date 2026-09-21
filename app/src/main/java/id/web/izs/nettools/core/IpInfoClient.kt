package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Generic IP-info client: works with ipwho.is, ip-api.com, ipaddress.to (no key). Base URL configurable. */
object IpInfoClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun lookup(target: String, base: String): Flow<String> = flow {
        val b = base.trim().trimEnd('/')
        val me = target.isEmpty()
        // Some free providers only report the caller's own IP (no lookup for others).
        val selfOnly = b.contains("api.ipify.org") ||
            b.contains("icanhazip.com") ||
            b.contains("amazonaws.com")
        val url = when {
            b.contains("{ip}") -> b.replace("{ip}", if (me) "my" else target)
            me && b.contains("ipaddress.to") -> "$b/my"
            me -> b
            selfOnly -> b
            b.contains("ip-api.com") -> "$b/$target?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query"
            b.contains("ipaddress.to") -> "$b/$target"
            else -> "$b/$target"
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
