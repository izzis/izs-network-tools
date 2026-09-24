package id.web.izs.nettools.core

import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * WiFi Analyzer: live AP list, no root, no target host.
 *
 * One scan cycle every [REFRESH_MS]: read `getScanResults()`, map to
 * [ApInfo], apply the current filters, emit one LIVE line per BSSID
 * (in-place RSSI updates — same mechanism as Globalping probes). The
 * connected AP's row is colored green in the UI (no marker, list order =
 * RSSI only). The target bar is free-text SSID **or** MAC filter;
 * band/channel/security are discrete chips. Stop = cancel the flow
 * (existing button).
 *
 * Android throttles `startScan()` to ~4 calls / 2 min; one kick per
 * 30 s cycle sits at that limit, so the call is guarded by
 * [lastStartScanMs]. Results between kicks come from the OS cache
 * (it rescans on its own while WiFi is up).
 */
object WifiAnalyzerRunner {

    const val REFRESH_MS = 30_000

    /** Band/channel/security chips + free-text query (SSID or MAC). */
    data class Filters(
        val query: String = "",   // SSID or BSSID substring, case-insensitive
        val band: String = "",    // "", "2.4", "5", "6"
        val channel: Int = -1,    // -1 = all
        val security: String = "" // "", "WPA3", "WPA2", "WPA", "WEP", "open"
    )

    data class ApInfo(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val frequency: Int, // MHz
        val security: String,
        val connected: Boolean = false
    )

    // --- pure helpers (unit-tested) ---

    fun bandOf(freqMhz: Int): String = when (freqMhz) {
        in 2400..2484 -> "2.4"
        in 4915..5895 -> "5"
        in 5925..7125 -> "6"
        else -> "?"
    }

    fun channelOf(freqMhz: Int): Int = when {
        freqMhz == 2484 -> 14
        freqMhz in 2400..2484 -> (freqMhz - 2407) / 5
        freqMhz in 4915..5895 -> (freqMhz - 5000) / 5
        freqMhz in 5925..7125 -> (freqMhz - 5950) / 5
        else -> 0
    }

    fun securityOf(capabilities: String): String = when {
        capabilities.contains("WEP", ignoreCase = true) -> "WEP"
        capabilities.contains("WPA3", ignoreCase = true) ||
            capabilities.contains("SAE", ignoreCase = true) -> "WPA3"
        capabilities.contains("WPA2", ignoreCase = true) ||
            capabilities.contains("RSN", ignoreCase = true) -> "WPA2"
        capabilities.contains("WPA", ignoreCase = true) -> "WPA"
        else -> "open"
    }

    fun matches(ap: ApInfo, f: Filters): Boolean {
        if (f.query.isNotEmpty() &&
            !ap.ssid.contains(f.query, ignoreCase = true) &&
            !ap.bssid.contains(f.query, ignoreCase = true)
        ) return false
        if (f.band.isNotEmpty() && bandOf(ap.frequency) != f.band) return false
        if (f.channel >= 0 && channelOf(ap.frequency) != f.channel) return false
        if (f.security.isNotEmpty() && !ap.security.equals(f.security, ignoreCase = true)) return false
        return true
    }

    /**
     * 8-step signal staircase: full (-30 dBm) = `▁▂▃▄▅▆▇█`,
     * half ≈ `▁▂▃▄`, weak = `▁` / empty at -100 dBm.
     * Length = level, so stronger APs read as a taller/wider stair.
     */
    fun barsOf(rssi: Int): String {
        val steps = "▁▂▃▄▅▆▇█"
        val level = ((rssi + 100).coerceIn(0, 70) * 8 / 70).coerceIn(0, 8)
        return steps.take(level)
    }

    /**
     * Two-line console row (monospace, no indent):
     *   line 1: SSID · signal stair · dBm
     *   line 2: MAC · channel · band · security · `(gone)`/`(filter)`
     * Hidden SSIDs show `(hidden)` on line 1 — the MAC on line 2 still
     * uniquely identifies the AP. Connected is a UI color (green), not a marker.
     */
    fun formatAp(ap: ApInfo, gone: Boolean = false): String {
        val name = ap.ssid.ifEmpty { "(hidden)" }.take(20)
        val line1 = buildString {
            append(name.padEnd(20))
            append(barsOf(ap.rssi).padEnd(8)) // reserve stair width so dBm lines up
            append(' ')
            append(ap.rssi.toString().padStart(4))
            append(" dBm")
        }
        val mark = if (gone) " (gone)" else ""
        // padEnd so band/security stay aligned across rows
        val ch = "ch${channelOf(ap.frequency).toString().padStart(3)}"
        val band = "${bandOf(ap.frequency)}G".padEnd(4)
        val line2 = "${ap.bssid}  $ch  $band ${ap.security}$mark"
        return "$line1\n$line2"
    }

    // --- runner ---

    @Suppress("DEPRECATION")
    fun scan(
        wifi: WifiManager?,
        filters: () -> Filters,
        onScanDone: () -> Unit = {},
        onChannels: (List<Int>) -> Unit = {},
        onConnected: (String) -> Unit = {}
    ): Flow<String> = flow {
        if (wifi == null) {
            emit("ERROR: WiFi service unavailable")
            return@flow
        }
        emit(";; refresh every ${REFRESH_MS / 1000}s — Stop ends the run, chips re-filter on the next cycle")
        val shown = mutableSetOf<String>()      // BSSIDs currently live on screen
        val rawCache = mutableMapOf<String, ApInfo>() // last raw sighting (for gone/filter notes)
        var lastStartScanMs = 0L
        try {
            // Kick the first scan and give the OS a moment to fill the cache.
            // startScan() needs CHANGE_WIFI_STATE; a throw must not kill the run
            // (getScanResults still works off the OS cache on its own cadence).
            try {
                wifi.startScan()
            } catch (_: SecurityException) {
            }
            lastStartScanMs = System.currentTimeMillis()
            delay(1200)
            while (true) {
                val f = filters()
                @Suppress("DEPRECATION")
                val info = wifi.connectionInfo
                // Android reports 02:00:00:00:00:00 when not associated.
                val connBssid = info?.bssid?.takeIf {
                    it.isNotEmpty() && it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00"
                }
                val raw = try {
                    wifi.scanResults.orEmpty().map { r ->
                        ApInfo(
                            bssid = r.BSSID.orEmpty(),
                            // SSID is the portable getter (WIFI_SSID is API 33+ only).
                            ssid = r.SSID.orEmpty(),
                            rssi = r.level,
                            frequency = r.frequency,
                            security = securityOf(r.capabilities.orEmpty()),
                            connected = connBssid != null && r.BSSID.equals(connBssid, ignoreCase = true)
                        )
                    }.filter { it.bssid.isNotEmpty() }
                } catch (e: SecurityException) {
                    emit(
                        "ERROR: scan permission denied — grant Location " +
                            "(and Nearby devices on Android 13+) and run again"
                    )
                    return@flow
                }
                raw.forEach { rawCache[it.bssid] = it }

                // Associated BSSID feeds the UI's green row highlight.
                onConnected(connBssid.orEmpty())

                val matching = raw.filter { matches(it, f) }.sortedByDescending { it.rssi }
                // Chip list follows ssid/band/security but ignores the channel
                // chip itself, so every selectable channel stays visible.
                onChannels(
                    raw.filter { matches(it, f.copy(channel = -1)) }
                        .map { channelOf(it.frequency) }.distinct().sorted()
                )

                // APs that vanished from the radio entirely → one (gone) note.
                val rawNow = raw.map { it.bssid }.toSet()
                for (bssid in shown.filter { it !in rawNow }) {
                    rawCache[bssid]?.let { emit("${GlobalpingRunner.LIVE}$bssid\n${formatAp(it, gone = true)}") }
                    shown.remove(bssid)
                    rawCache.remove(bssid)
                }
                // APs still on air but knocked out by the current filter.
                for (bssid in shown.filter { b -> matching.none { it.bssid == b } }) {
                    rawCache[bssid]?.let { emit("${GlobalpingRunner.LIVE}$bssid\n${formatAp(it)}  (filter)") }
                    shown.remove(bssid)
                }
                // Live rows (replace in place on later cycles).
                for (ap in matching) {
                    emit("${GlobalpingRunner.LIVE}${ap.bssid}\n${formatAp(ap)}")
                    shown.add(ap.bssid)
                }
                if (raw.isEmpty()) {
                    // OEMs often log "no location permission" and return an empty
                    // list instead of throwing — never say "just Nearby" here.
                    emit(
                        ";; 0 APs — grant Location permission for this app, " +
                            "turn on Location (GPS), then tap Run"
                    )
                } else if (matching.isEmpty()) {
                    emit(";; ${raw.size} APs on air, none match the current filter")
                }
                onScanDone()
                // Throttled restart of an active scan; reads below use the cache.
                val now = System.currentTimeMillis()
                if (now - lastStartScanMs >= REFRESH_MS) {
                    try {
                        wifi.startScan()
                    } catch (_: SecurityException) {
                    }
                    lastStartScanMs = now
                }
                delay(REFRESH_MS.toLong())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit("ERROR: WiFi scan failed (${e.message})")
        }
    }.flowOn(Dispatchers.IO)
}
