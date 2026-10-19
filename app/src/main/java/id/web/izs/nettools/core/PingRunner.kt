package id.web.izs.nettools.core

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private val pingSeq = Regex("""icmp_seq=(\d+)""")
private val pingTransmitted = Regex("""(\d+) packets transmitted""")

/** Ping via system binary, streams each output line. No root needed.
 *  [count] <= 0 means nonstop until cancelled (no "-c" flag).
 *
 *  The binary prints nothing for a lost packet, so each seq gets a realtime
 *  deadline: ping sends 1 packet/s and waits [timeoutSec] per reply, hence seq
 *  N is declared lost (red "Request timeout for icmp_seq=N") once (N-1)s +
 *  wait + grace has passed with no reply. Marks stream live in packet rhythm,
 *  including nonstop mode — never batched at the end. At the binary's final
 *  summary, every seq still open is flushed right there: the LAST seq's window
 *  closes exactly when ping exits, i.e. always before its own deadline. */
object PingRunner {
    /** Cap on the pre-spawn target resolve; on expiry ping resolves itself. */
    private const val TARGET_RESOLVE_MS = 1500L

    // -W matches the 1s send interval: per-seq RTO markers then land ~0.3s
    // apart in send rhythm (near-ordered). Larger -W would delay marks and
    // scatter the order; smaller risks false RTOs on slow (>1s RTT) hosts.
    fun ping(host: String, count: Int = 4, timeoutSec: Int = 1): Flow<String> = callbackFlow {
        if (!ExecUtil.pingAvailable()) {
            trySend("ERROR: ping binary not found on this device.")
            close()
            return@callbackFlow
        }
        // Resolve BEFORE spawning: the per-seq RTO clock below is anchored to
        // process start, so DNS time spent inside ping would shift every
        // deadline — eating the 300ms reply grace (mobile DNS often costs that
        // much) and, once over the grace, marking a seq as timed out before
        // ping has even sent it. Unresolvable targets fall back to letting
        // ping resolve itself, as before.
        val target = withTimeoutOrNull(TARGET_RESOLVE_MS) { TraceRunner.resolveIp(host) } ?: host
        val args = mutableListOf<String>()
        if (count > 0) { args += "-c"; args += count.toString() }
        args += listOf("-W", timeoutSec.toString(), target)
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
        // Monotonic clock: a wall-clock step (NTP sync mid-run) would move
        // every per-seq deadline — same rationale as NetDevWatcher.
        val startAt = SystemClock.elapsedRealtime()
        // Replied seqs (reader fills, monitor reads). Only real "bytes from"
        // replies count — "no answer yet" lines carry a seq but are not replies.
        val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
        val markedUpTo = AtomicInteger(0)
        val finished = AtomicBoolean(false)
        // The monitor's mark loop, reusable at the summary line: ping's
        // transmitted count is the authority on what was sent, so any seq still
        // open up to there is lost — CAS keeps this race-free against a monitor
        // tick that is mid-flight while this runs.
        fun flushLost(upTo: Int) {
            while (true) {
                val next = markedUpTo.get() + 1
                if (next > upTo) break
                if (!markedUpTo.compareAndSet(next - 1, next)) continue
                if (next !in seen) trySend("Request timeout for icmp_seq=$next")
            }
        }
        // Per-seq deadlines: seq N is sent at (N-1)s, so mark it the moment its
        // wait window lapses. Ticks stream marks live in send rhythm for both
        // counted and nonstop runs.
        val monitor = launch(Dispatchers.Default) {
            while (isActive && !finished.get()) {
                delay(250)
                if (finished.get()) break
                val elapsed = SystemClock.elapsedRealtime() - startAt
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
            // The stats preamble — a blank line, then
            // `--- <target> ping statistics ---` — is held back until the
            // transmitted count arrives one line below it: that count is what
            // bounds flushLost(), and the flushed marks must print ABOVE the
            // whole block, not wedged between its header and its numbers.
            val held = ArrayList<String>()
            fun emitHeld() {
                held.forEach { trySend(it) }
                held.clear()
            }
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    pingSeq.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        if (line.contains("bytes from")) seen.add(it)
                    }
                    if (line.isBlank() || line.contains("ping statistics")) {
                        held += line
                        return@forEachLine
                    }
                    if (line.contains("packets transmitted")) {
                        finished.set(true)
                        monitor.cancel()
                        // The summary is ping's own verdict on what it sent, so
                        // every seq up to that count without a reply line above
                        // is lost now — chief among them the LAST one: its wait
                        // window (-W) closes exactly when the process exits, i.e.
                        // always before its own RTO deadline ((N-1)s + -W +
                        // grace), so the monitor never gets to it. Without this
                        // flush, "4 packets transmitted" showed only 3 timeout
                        // rows, seq stuck at 3.
                        pingTransmitted.find(line)?.groupValues?.get(1)?.toIntOrNull()
                            ?.let { flushLost(it) }
                        emitHeld()
                    } else if (held.isNotEmpty()) {
                        // Not the count line: whatever this is, the held preamble
                        // belongs in front of it.
                        emitHeld()
                    }
                    trySend(line)
                }
            } catch (_: Exception) {
            } finally {
                finished.set(true)
                // A stream that ends mid-block (aborted run, trailing blank
                // line) still owes the reader its held lines.
                emitHeld()
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
