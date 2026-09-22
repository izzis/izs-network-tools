package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val pingSeq = Regex("""icmp_seq=(\d+)""")

/** Ping via system binary, streams each output line. No root needed.
 *  [count] <= 0 means nonstop until cancelled (no "-c" flag).
 *
 *  The binary prints nothing for a lost packet, so each seq gets a realtime
 *  deadline: ping sends 1 packet/s and waits [timeoutSec] per reply, hence seq
 *  N is declared lost (red "Request timeout for icmp_seq=N") once (N-1)s +
 *  wait + grace has passed with no reply. Marks stream live in packet rhythm,
 *  including nonstop mode — never batched at the end. */
object PingRunner {
    // -W matches the 1s send interval: per-seq RTO markers then land ~0.3s
    // apart in send rhythm (near-ordered). Larger -W would delay marks and
    // scatter the order; smaller risks false RTOs on slow (>1s RTT) hosts.
    fun ping(host: String, count: Int = 4, timeoutSec: Int = 1): Flow<String> = callbackFlow {
        if (!ExecUtil.pingAvailable()) {
            trySend("ERROR: ping binary not found on this device.")
            close()
            return@callbackFlow
        }
        val args = mutableListOf<String>()
        if (count > 0) { args += "-c"; args += count.toString() }
        args += listOf("-W", timeoutSec.toString(), host)
        val proc = try {
            ProcessBuilder(listOf(ExecUtil.pingBin()) + args)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            trySend("failed to start ping: ${e.message}")
            close()
            return@callbackFlow
        }
        val deadlineMs = timeoutSec * 1000L + 300L
        val startAt = System.currentTimeMillis()
        // Replied seqs (reader fills, monitor reads). Only real "bytes from"
        // replies count — "no answer yet" lines carry a seq but are not replies.
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        val markedUpTo = AtomicInteger(0)
        val finished = AtomicBoolean(false)
        // Per-seq deadlines: seq N is sent at (N-1)s, so mark it the moment its
        // wait window lapses. Ticks stream marks live in send rhythm for both
        // counted and nonstop runs.
        val monitor = launch(Dispatchers.Default) {
            while (isActive && !finished.get()) {
                delay(250)
                if (finished.get()) break
                val elapsed = System.currentTimeMillis() - startAt
                while (true) {
                    val next = markedUpTo.get() + 1
                    if (count > 0 && next > count) break
                    if ((next - 1) * 1000L + deadlineMs > elapsed) break
                    if (!markedUpTo.compareAndSet(next - 1, next)) continue
                    if (next !in seen) trySend("Request timeout for icmp_seq=$next")
                }
            }
        }
        val t = Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    pingSeq.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (line.contains("bytes from")) seen.add(it)
                    }
                    if (line.contains("packets transmitted")) {
                        finished.set(true)
                        monitor.cancel()
                    }
                    trySend(line)
                }
            } catch (_: Exception) {
            } finally {
                finished.set(true)
                close()
            }
        }
        t.isDaemon = true
        t.start()
        awaitClose {
            finished.set(true)
            monitor.cancel()
            try {
                proc.destroy()
            } catch (_: Exception) {
            }
            try {
                t.interrupt()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)
}
