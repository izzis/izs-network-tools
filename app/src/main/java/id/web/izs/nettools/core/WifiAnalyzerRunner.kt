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
 * [ApInfo], apply the current filters, emit LIVE lines (in-place updates —
 * same mechanism as Globalping probes). Display mode [Filters.display]:
 * `list` = one row per BSSID (RSSI desc, connected row green in the UI);
 * `channel` = per-channel overlap counts (an AP counts toward every channel
 * whose center falls inside the AP's occupied bandwidth), rows sorted by
 * channel number. The target bar is free-text SSID **or** MAC filter; band /
 * channel / security are discrete chips. Stop = cancel the flow.
 *
 * Android throttles `startScan()` to ~4 calls / 2 min; one kick per
 * 30 s cycle sits at that limit, so the call is guarded by
 * [lastStartScanMs]. Results between kicks come from the OS cache
 * (it rescans on its own while WiFi is up).
 */
object WifiAnalyzerRunner {

    const val REFRESH_MS = 30_000

    /** Display modes for [Filters.display]. */
    const val DISPLAY_LIST = "list"
    const val DISPLAY_CHANNEL = "channel"

    /**
     * Query + multi-select chips + single-select channel/display.
     * [band] / [security]: empty set = nothing selected (match nothing);
     * default = every option. Within a chip group items are OR-ed; groups AND.
     */
    data class Filters(
        val query: String = "",   // SSID or BSSID substring, case-insensitive
        val band: Set<String> = setOf("2.4", "5", "6"),
        val channel: Int = -1,    // -1 = all
        val security: Set<String> = setOf("WPA3", "WPA2", "WPA", "WEP", "open"),
        val display: String = DISPLAY_LIST // "list" | "channel"
    )

    data class ApInfo(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val frequency: Int, // MHz (primary)
        val security: String,
        val connected: Boolean = false,
        /** Center frequency (MHz); 0 = fall back to [frequency]. */
        val centerFreq: Int = 0,
        /** Occupied bandwidth in MHz (20/40/80/160). */
        val widthMhz: Int = 20
    )

    /** One row of the Channel display: APs whose spectrum overlaps this channel. */
    data class ChannelCrowd(
        val channel: Int,
        val count: Int,
        val band: String
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

    /** Center frequency of [ch] in [band] ("2.4" / "5" / "6"), or null if n/a. */
    fun channelFreqOf(ch: Int, band: String): Int? = when {
        band == "2.4" && ch == 14 -> 2484
        band == "2.4" && ch in 1..13 -> 2407 + 5 * ch
        band == "5" && ch >= 1 -> 5000 + 5 * ch
        band == "6" && ch >= 1 -> 5950 + 5 * ch
        else -> null
    }

    /** Frequency range this AP occupies (center ± width/2). */
    fun occupiedRange(ap: ApInfo): IntRange {
        val center = ap.centerFreq.takeIf { it > 0 } ?: ap.frequency
        val half = ap.widthMhz / 2
        return (center - half)..(center + half)
    }

    /** True when [channelFreq] (channel center) falls inside the AP's spectrum. */
    fun overlaps(ap: ApInfo, channelFreq: Int): Boolean = channelFreq in occupiedRange(ap)

    /** ScanResult.channelWidth → MHz (0=20, 1=40, 2=80, 3=160, 4=80+80). */
    fun widthMhzOf(channelWidth: Int): Int = when (channelWidth) {
        1 -> 40
        2 -> 80
        3, 4 -> 160
        else -> 20
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
        if (bandOf(ap.frequency) !in f.band) return false
        if (f.channel >= 0 && channelOf(ap.frequency) != f.channel) return false
        if (f.security.none { it.equals(ap.security, ignoreCase = true) }) return false
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

    /**
     * Per-channel overlap counts for the Channel display: an AP is counted on
     * every channel whose center frequency lies inside the AP's occupied
     * bandwidth (so ch 1 + ch 3 APs both hit ch 2 — same model as VREM).
     * Rows are sorted by channel number only. Empty 2.4 GHz channels 1–14 are
     * listed when "2.4" ∈ `f.band`; `f.channel` (if ≥ 0) keeps only that row.
     * Pass `aps` with the channel chip ignored (`f.copy(channel = -1)`) so
     * adjacent primaries still overlap the focus.
     */
    fun channelCrowding(aps: List<ApInfo>, f: Filters = Filters()): List<ChannelCrowd> {
        val pairs = mutableSetOf<Pair<String, Int>>()
        val seed24 = "2.4" in f.band && (f.channel < 0 || f.channel <= 14)
        if (seed24) for (ch in 1..14) pairs += "2.4" to ch
        for (ap in aps) {
            val band = bandOf(ap.frequency)
            if (band == "?") continue
            val range = occupiedRange(ap)
            val primary = channelOf(ap.frequency)
            // Walk a window wide enough for 160 MHz (±32 × 5 MHz channels).
            val lo = (primary - 32).coerceAtLeast(1)
            val hi = primary + 32
            for (ch in lo..hi) {
                val cf = channelFreqOf(ch, band) ?: continue
                if (cf in range) pairs += band to ch
            }
        }
        if (f.channel >= 0 && pairs.none { it.second == f.channel }) {
            // Quiet focus: one row for the pinned channel, first selected band that has it.
            val b = listOf("2.4", "5", "6")
                .firstOrNull { it in f.band && channelFreqOf(f.channel, it) != null }
            if (b != null) pairs += b to f.channel
        }
        return pairs
            .map { (band, ch) ->
                val cf = channelFreqOf(ch, band) ?: return@map null
                ChannelCrowd(
                    channel = ch,
                    count = aps.count { overlaps(it, cf) },
                    band = band
                )
            }
            .filterNotNull()
            .filter { it.band in f.band }
            .filter { f.channel < 0 || it.channel == f.channel }
            .sortedWith(compareBy({ it.channel }, { it.band }))
    }

    /** One Channel-display row: `ch  N  bandG  K APs  ███…`. */
    fun formatChannelCrowd(c: ChannelCrowd): String {
        val ch = "ch ${c.channel.toString().padStart(3)}"
        val band = "${c.band}G".padEnd(4)
        val n = when {
            c.count == 0 -> "   0 AP"
            c.count == 1 -> "   1 AP"
            else -> "${c.count} APs".padStart(7)
        }
        val bar = if (c.count <= 0) "" else "  " + "█".repeat(c.count.coerceAtMost(32))
        return "$ch  $band  $n$bar"
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
                            connected = connBssid != null && r.BSSID.equals(connBssid, ignoreCase = true),
                            centerFreq = r.centerFreq0,
                            widthMhz = widthMhzOf(r.channelWidth)
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

                if (f.display == DISPLAY_CHANNEL) {
                    // Overlap view: one LIVE row per channel, sorted by channel no.
                    // Channel chip picks the focus row; count with channel ignored
                    // so adjacent primaries still overlap it (VREM model).
                    val forCrowd = raw.filter { matches(it, f.copy(channel = -1)) }
                    for (c in channelCrowding(forCrowd, f)) {
                        emit("${GlobalpingRunner.LIVE}ch:${c.channel}\n${formatChannelCrowd(c)}")
                    }
                } else {
                    // List view: APs that vanished from the radio entirely → one (gone) note.
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
