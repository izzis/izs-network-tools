package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/** Helpers to detect + stream real system binaries (ping, traceroute). */
object ExecUtil {

    /** True if a binary exists in PATH (e.g. "traceroute"). Cached per process. */
    private val cache = mutableMapOf<String, Boolean>()

    @Synchronized
    fun exists(cmd: String): Boolean = cache.getOrPut(cmd) {
        try {
            val p = ProcessBuilder("sh", "-c", "command -v $cmd")
                .redirectErrorStream(true)
                .start()
            p.inputStream.bufferedReader().readText()
            p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    /** Absolute path to ping binary. App processes often have a minimal PATH,
     *  so never rely on bare "ping". */
    fun pingBin(): String {
        val sys = java.io.File("/system/bin/ping")
        if (sys.exists() && sys.canExecute()) return sys.absolutePath
        return "ping"
    }

    fun pingAvailable(): Boolean {
        val sys = java.io.File("/system/bin/ping")
        if (sys.exists()) return true
        return exists("ping")
    }

    /** Stream stdout+stderr lines of a command. Destroyed on cancel (Stop button). */
    fun stream(vararg cmd: String): Flow<String> = callbackFlow {
        val proc = try {
            ProcessBuilder(*cmd).redirectErrorStream(true).start()
        } catch (e: Exception) {
            trySend("failed to run ${cmd[0]}: ${e.message}")
            close()
            return@callbackFlow
        }
        val t = Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line -> trySend(line) }
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
