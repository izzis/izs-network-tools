package id.web.izs.nettools.core

import id.web.izs.nettools.model.HopInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Traceroute without root: TTL-limited ping probes per hop.
 * On Android, unprivileged ICMP goes through the ping binary, which is enough
 * to discover hops that answer "Time exceeded".
 *
 * Pure traceroute only: no loop verdict, no early-stop on loops. Loop
 * analysis (L2 storm + L3 routing loop) lives in LoopRunner, which reuses
 * [probe] as its per-TTL primitive.
 */
object TraceRunner {

    private val fromIp = Regex("""[Ff]rom\s+([0-9a-fA-F.:]+)""")
    private val bytesFrom = Regex("""bytes from\s+([0-9a-fA-F.:()\[\]\w.-]+)""")
    private val rtt = Regex("""time[=<]([0-9.]+)\s*ms""")

    private val ipToken =
        Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b|\b[0-9a-fA-F]*:[0-9a-fA-F:]*[0-9a-fA-F]\b""")

    /** First IP on a numeric `traceroute -n` output line, null for `*` rows/headers. */
    fun parseBinaryHopLine(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("traceroute to")) return null
        if (!t[0].isDigit()) return null
        return ipToken.find(t)?.value
    }

    suspend fun probe(host: String, ttl: Int, timeoutSec: Int = 2): HopInfo? {
        val proc = try {
            ProcessBuilder(ExecUtil.pingBin(), "-c", "2", "-W", timeoutSec.toString(), "-t", ttl.toString(), host)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return null
        }
        return try {
            if (!proc.waitFor((timeoutSec * 3 + 2).toLong(), TimeUnit.SECONDS)) {
                proc.destroy()
            }
            val out = proc.inputStream.bufferedReader().readText()
            var ip: String? = null
            var rttMs: Double? = null
            var reached = false
            for (line in out.lines()) {
                fromIp.find(line)?.let { ip = it.groupValues[1].trimEnd(':') }
                if (ip == null) {
                    bytesFrom.find(line)?.let { m ->
                        var v = m.groupValues[1].trim('(', ')', '[', ']', ':')
                        // "host (1.2.3.4)" form
                        val paren = Regex("""\(([^)]+)\)""").find(line)
                        if (paren != null) v = paren.groupValues[1]
                        ip = v
                        reached = true
                    }
                }
                rtt.find(line)?.let { rttMs = it.groupValues[1].toDoubleOrNull() }
            }
            var name: String? = null
            if (ip != null) {
                name = withTimeoutOrNull(1500) {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        try {
                            InetAddress.getByName(ip).hostName
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (name == ip) name = null
            }
            HopInfo(ttl, ip, name, rttMs, reached)
        } catch (_: Exception) {
            HopInfo(ttl, null, null, null, false)
        } finally {
            try {
                proc.destroy()
            } catch (_: Exception) {
            }
        }
    }

    /** Resolve target to an IP once, so we can stop as soon as that IP answers. */
    suspend fun resolveIp(host: String): String? = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(host).hostAddress
        } catch (_: Exception) {
            null
        }
    }

    fun formatHop(h: HopInfo): String {
        val dest = h.ip ?: "*"
        val name = if (h.hostName != null) " (${h.hostName})" else ""
        val ms = if (h.rttMs != null) "  ${h.rttMs} ms" else ""
        return "%2d  %s%s%s".format(h.ttl, dest, name, ms)
    }

    fun traceroute(
        host: String,
        maxHops: Int,
        onProgress: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        if (ExecUtil.exists("traceroute")) {
            emit("traceroute to $host [backend: system traceroute binary]")
            ExecUtil.stream("traceroute", "-n", "-m", maxHops.toString(), "-w", "2", host)
                .collect { line -> emit(line) }
            return@flow
        }
        if (!ExecUtil.pingAvailable()) {
            emit("ERROR: ping binary not found on this device, cannot trace.")
            return@flow
        }
        val destIp = resolveIp(host)
        // Single blue summary line: backend, destination, and the '*' legend.
        emit("traceroute to $host [TTL-ping: no binary, dest ${destIp ?: "unresolved"}, silent hops show *]")
        var prevIp: String? = null
        var resolved = 0
        for (ttl in 1..maxHops) {
            onProgress?.invoke("Probing hop $ttl/$maxHops...")
            val h = probe(host, ttl)
            if (h == null) {
                emit("ERROR: failed to run ping for hop $ttl.")
                break
            }
            if (h.ip != null) resolved++
            val arrived = h.reached || (destIp != null && h.ip == destIp)
            val line = if (!arrived && h.ip != null && h.ip == prevIp) {
                "${formatHop(h)}  [same as hop ${ttl - 1}, typical for anycast/MPLS]"
            } else {
                formatHop(h)
            }
            if (h.ip != null) prevIp = h.ip
            emit(line)
            if (arrived) {
                emit("Destination reached in $ttl hops. Stopping.")
                break
            }
            if (ttl == maxHops) emit("Max hops reached.")
        }
        if (resolved == 0) {
            emit("Note: no hop answered. The destination may block ICMP, or this")
            emit("device's ping may ignore the TTL flag.")
        }
    }.flowOn(Dispatchers.IO)
}
