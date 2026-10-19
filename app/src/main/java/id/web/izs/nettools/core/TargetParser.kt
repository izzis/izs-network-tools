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

    private val parenGroup = Regex("""\(([^)]+)\)""")

    /**
     * The resolved destination inside a Linux-style intro line —
     * `PING host (1.2.3.4) 56(84) bytes of data.`,
     * `traceroute to host (1.2.3.4), 30 hops max, 60 byte packets`,
     * TraceRunner's TTL-ping variant — or null when the line carries no such
     * address (binary-path intro, `(unresolved)`), or when it only repeats the
     * target itself (pinging a literal IP: `(8.8.8.8)` adds nothing).
     */
    fun resolvedIpIn(line: String, host: String): String? {
        val paren = parenGroup.find(line)?.groupValues?.get(1)?.trim() ?: return null
        if (!isIp(paren)) return null
        val ip = paren.trim('[', ']')
        return if (ip.equals(host.trim('[', ']'), ignoreCase = true)) null else ip
    }

    /**
     * [header] with the resolved destination inserted after its target —
     * `== PING google.com [via …] ==` becomes `== PING google.com (1.2.3.4) [via …] ==`
     * — or null when [line] carries nothing to fold ([resolvedIpIn]) or does not
     * match the [headerPrefix] built for this run. Callers drop [line] on success,
     * so the run keeps exactly ONE intro line, now showing where packets go even
     * if every probe times out.
     */
    fun withResolvedIp(header: String, headerPrefix: String, line: String, host: String): String? {
        val ip = resolvedIpIn(line, host) ?: return null
        if (!header.startsWith(headerPrefix)) return null
        return header.replaceRange(headerPrefix.length, headerPrefix.length, "($ip) ")
    }
}
