package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/** Ping via system binary, streams each output line. No root needed.
 *  [count] <= 0 means nonstop until cancelled (no "-c" flag). */
object PingRunner {
    fun ping(host: String, count: Int = 4, timeoutSec: Int = 2): Flow<String> = callbackFlow {
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
        val t = Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    trySend(line)
                }
            } catch (_: Exception) {
            } finally {
                close()
            }
        }
        t.isDaemon = true
        t.start()
        awaitClose {
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
