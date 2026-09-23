package id.web.izs.nettools.core

import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The Neighbor tool: three listen phases, one LAN.
 *
 * 1. MNDP — MikroTik devices, even without an IP;
 * 2. mDNS — local services (printers, casts, cameras…);
 * 3. SSDP — UPnP devices (TVs, routers, NAS…).
 *
 * All three are plain-UDP broadcast/multicast: no target, no local IP,
 * no root. The Scan timeout setting is split across the phases so one
 * run stays a few seconds, not three timeouts back to back.
 */
object NeighborRunner {

    fun discover(
        wifi: WifiManager?,
        onProgress: ((String) -> Unit)? = null,
        listenMs: Int = 3000
    ): Flow<String> = flow {
        val perPhase = (listenMs / 3).coerceIn(2000, 5000)
        try {
            emitAll(MndpDiscover.discover(onProgress, perPhase))
            emitAll(MdnsDiscover.discover(wifi, onProgress, perPhase))
            emitAll(SsdpDiscover.discover(wifi, onProgress, perPhase))
        } catch (e: Exception) {
            emit("ERROR: neighbor discovery failed (${e.message})")
        }
    }.flowOn(Dispatchers.IO)
}
