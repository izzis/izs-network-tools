package id.web.izs.nettools.core

/** Parse user input like "https://example.com:8080/path" into host + optional port. */
object TargetParser {
    data class Target(val host: String, val port: Int?)

    fun parse(raw: String): Target {
        var s = raw.trim()
        // strip scheme
        val schemeIdx = s.indexOf("://")
        if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)
        // strip path/query
        for (c in listOf('/', '?', '#')) {
            val i = s.indexOf(c)
            if (i >= 0) s = s.substring(0, i)
        }
        s = s.trim().trimEnd('.')
        // IPv6 in brackets
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end > 0) {
                val host = s.substring(1, end)
                val rest = s.substring(end + 1)
                val port = if (rest.startsWith(":")) rest.substring(1).toIntOrNull() else null
                return Target(host, port)
            }
        }
        // host:port (single colon, not IPv6)
        if (s.count { it == ':' } == 1) {
            val parts = s.split(":")
            val port = parts[1].toIntOrNull()
            if (port != null && parts[0].isNotEmpty()) return Target(parts[0], port)
        }
        return Target(s, null)
    }

    fun isIp(s: String): Boolean {
        return s.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || s.contains(':')
    }
}
