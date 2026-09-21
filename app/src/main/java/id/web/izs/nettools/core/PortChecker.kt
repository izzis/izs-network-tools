package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetSocketAddress
import java.net.Socket

/** Fast TCP port check. Supports "host:port" for single-port mode. */
object PortChecker {

    val commonPorts = listOf(21, 22, 25, 53, 80, 110, 143, 443, 465, 587, 993, 995, 3306, 8080, 8443)
    const val MAX_PORTS = 1000

    /**
     * Parse a port list like "22,80,8000-8010" (spaces, commas, semicolons or
     * new lines separate entries, "-" marks an inclusive range, reversed
     * ranges are tolerated). Invalid tokens are ignored.
     * Falls back to defaults if nothing valid.
     */
    fun parsePorts(raw: String): List<Int> {
        val out = mutableSetOf<Int>()
        for (token in raw.split(Regex("[\\s,;]+"))) {
            if (token.isBlank()) continue
            val dash = token.split("-")
            if (dash.size == 2) {
                val a = dash[0].toIntOrNull()
                val b = dash[1].toIntOrNull()
                if (a != null && b != null) {
                    val lo = minOf(a, b).coerceIn(1, 65535)
                    val hi = maxOf(a, b).coerceIn(1, 65535)
                    if (hi - lo <= 5000) {
                        for (p in lo..hi) out.add(p)
                    }
                    continue
                }
            }
            token.toIntOrNull()?.let { if (it in 1..65535) out.add(it) }
        }
        return out.sorted().ifEmpty { commonPorts }
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): Triple<Int, Boolean, Long> {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
            }
            Triple(port, true, System.currentTimeMillis() - start)
        } catch (_: Exception) {
            Triple(port, false, System.currentTimeMillis() - start)
        }
    }

    fun check(host: String, singlePort: Int?, timeoutMs: Int, ports: List<Int> = commonPorts): Flow<String> = flow {
        val limited = ports.take(MAX_PORTS)
        val portList = if (singlePort != null) listOf(singlePort) else limited
        emit(";; checking ${portList.size} TCP ports on $host (timeout ${timeoutMs}ms)")
        if (singlePort == null && ports.size > limited.size) {
            emit(";; note: list capped at $MAX_PORTS ports")
        }
        emit("")
        val results = coroutineScope {
            portList.map { p ->
                async(Dispatchers.IO) { probe(host, p, timeoutMs) }
            }.awaitAll()
        }
        var open = 0
        results.sortedBy { it.first }.forEach { (port, ok, ms) ->
            if (ok) {
                open++
                emit("OPEN   $port/tcp  (${ms} ms)")
            } else {
                emit("closed $port/tcp")
            }
        }
        emit("\nDone: $open/${portList.size} ports open.")
    }.flowOn(Dispatchers.IO)
}
