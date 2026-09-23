package id.web.izs.nettools.core

import android.net.wifi.WifiManager

/**
 * Best-effort WiFi multicast lock for mDNS/SSDP listeners.
 *
 * Most ROMs filter multicast at the firmware level unless an app holds
 * this lock — without it the Neighbor phases hear nothing and say so
 * honestly. `CHANGE_WIFI_MULTICAST_STATE` is a normal permission
 * (auto-granted, no runtime prompt), so acquire failures are rare and
 * never fatal: callers proceed anyway and report what they heard.
 */
object MulticastLock {

    fun acquire(wifi: WifiManager?, tag: String): WifiManager.MulticastLock? = try {
        wifi?.createMulticastLock(tag)?.apply {
            setReferenceCounted(true)
            acquire()
        }
    } catch (_: Exception) {
        null
    }

    fun release(lock: WifiManager.MulticastLock?) {
        try {
            if (lock != null && lock.isHeld) lock.release()
        } catch (_: Exception) {
        }
    }
}
