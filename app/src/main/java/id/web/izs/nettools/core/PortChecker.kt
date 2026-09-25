package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Fast TCP port check. Supports "host:port" for single-port mode. */
object PortChecker {

    val commonPorts = listOf(21, 22, 25, 53, 80, 110, 143, 443, 465, 587, 993, 995, 3306, 8080, 8443)
    const val MAX_PORTS = 65535

    /** Well-known services (144 entries, ascending) for ports whose protocol
     *  we never got to see — the plain, unconfirmed `?` label on OPEN lines. */
    val serviceByPort: Map<Int, String> = mapOf(
        7 to "echo", 9 to "discard", 13 to "daytime", 17 to "qotd", 19 to "chargen",
        20 to "ftp-data", 21 to "ftp", 22 to "ssh", 23 to "telnet", 24 to "lmtp",
        25 to "smtp", 37 to "time", 43 to "whois", 53 to "dns", 70 to "gopher",
        79 to "finger", 80 to "http", 88 to "kerberos", 102 to "iso-tsap",
        109 to "pop2", 110 to "pop3", 111 to "rpcbind",
        113 to "ident", 119 to "nntp", 123 to "ntp", 135 to "msrpc",
        139 to "netbios", 143 to "imap", 161 to "snmp", 162 to "snmptrap",
        179 to "bgp", 194 to "irc", 389 to "ldap", 443 to "https", 445 to "smb",
        464 to "kpasswd", 465 to "smtps", 500 to "isakmp", 514 to "syslog",
        515 to "lpd", 520 to "rip", 548 to "afp", 554 to "rtsp", 563 to "nntps",
        587 to "smtp", 631 to "ipp", 636 to "ldaps", 646 to "ldp", 873 to "rsync",
        902 to "vmware", 990 to "ftps", 992 to "telnets", 993 to "imaps",
        995 to "pop3s", 1080 to "socks", 1099 to "rmiregistry", 1194 to "openvpn",
        1433 to "mssql", 1434 to "mssql-monitor", 1521 to "oracle",
        1720 to "h323", 1723 to "pptp", 1883 to "mqtt", 1935 to "rtmp",
        2049 to "nfs", 2082 to "cpanel", 2083 to "cpanel-ssl", 2086 to "whm",
        2087 to "whm-ssl", 2181 to "zookeeper", 2375 to "docker",
        2376 to "docker-tls", 2379 to "etcd", 2380 to "etcd-peer",
        2483 to "oracle-jdbc", 2484 to "oracle-jdbc-ssl", 3000 to "http-alt",
        3128 to "proxy", 3260 to "iscsi", 3306 to "mysql", 3389 to "rdp",
        3690 to "svn", 4190 to "sieve", 4222 to "nats", 4899 to "radmin",
        5000 to "http-alt", 5060 to "sip", 5061 to "sip-tls", 5222 to "xmpp",
        5269 to "xmpp-server", 5432 to "postgres", 5601 to "kibana",
        5672 to "amqp", 5800 to "vnc-http", 5900 to "vnc", 5901 to "vnc",
        5984 to "couchdb", 5985 to "winrm", 5986 to "winrm-ssl", 6000 to "x11",
        6032 to "proxysql-admin", 6033 to "proxysql", 61613 to "stomp",
        61616 to "activemq", 6379 to "redis", 6432 to "pgbouncer",
        6443 to "kubernetes", 6667 to "irc", 7474 to "neo4j", 7547 to "cwmp",
        7687 to "neo4j-bolt", 8000 to "http-alt", 8006 to "proxmox",
        8008 to "http-alt", 8009 to "ajp", 8080 to "http-alt",
        8081 to "http-alt", 8082 to "http-alt", 8086 to "influxdb",
        8088 to "http-alt", 8091 to "couchbase", 8123 to "clickhouse",
        8161 to "activemq-web", 8443 to "https", 8500 to "consul",
        8888 to "http-alt", 9000 to "http-alt", 9042 to "cassandra",
        9090 to "prometheus", 9092 to "kafka", 9093 to "alertmanager",
        9100 to "printer", 9200 to "elasticsearch",
        9300 to "elasticsearch-transport", 9418 to "git", 9999 to "http-alt",
        10000 to "webmin", 10250 to "kubelet", 11211 to "memcached",
        15672 to "rabbitmq-mgmt", 25565 to "minecraft",
        27017 to "mongodb", 27018 to "mongodb", 27019 to "mongodb"
    )

    /** "Web" group: HTTP(S)-speaking ports of the service map. */
    val webPorts: List<Int> = serviceByPort
        .filterValues { it in setOf("http", "https", "http-alt", "ajp") }
        .keys
        .sorted()

    /** "Service" group: mainstream non-web services — mail, remote access,
     *  file transfer, network infra, databases, queues. Every entry also
     *  lives in serviceByPort, so All known stays the superset. */
    val servicePorts: List<Int> = listOf(
        24, 88, 111, 123, 135, 139, 161, 389, 445, 464, 500, 514, 554,
        631, 636, 873, 902, 990, 1080, 1099, 1194, 1433, 1434, 1521,
        1723, 1883, 2049, 2082, 2083, 2086, 2087, 2181, 2375, 2376,
        2379, 2483, 2484, 3128, 3260, 3306, 3389, 3690, 4190, 4222,
        5060, 5061, 5222, 5432, 5672, 5800, 5900, 5901, 5984, 5985,
        5986, 6032, 6033, 61613, 61616, 6379, 6432, 6443, 7547,
        8086, 8161, 8500, 9042, 9090, 9092, 9200, 9418, 10000, 10250,
        11211, 15672, 25565, 27017, 27018, 27019
    ).sorted()

    /** Quick-fill presets for the scan list: Default crossed with the Web and
     *  Service groups, plus everything that has a fallback label. Label ->
     *  compact list string in parsePorts syntax. Shown in the Ports
     *  long-press dialog; the pick is written straight back to the Settings
     *  port list (exotic scans stay a manual Custom entry there). */
    val scanPresets: Map<String, String> = run {
        fun combo(label: String, extra: List<Int>): Pair<String, String> {
            val ports = (commonPorts + extra).distinct().sorted()
            return "$label (${ports.size})" to ports.joinToString(",")
        }
        linkedMapOf(
            combo("Default", emptyList()),
            combo("+ Web", webPorts),
            combo("+ Service", servicePorts),
            combo("+ Both", webPorts + servicePorts),
            "All known (${serviceByPort.size})" to serviceByPort.keys.sorted().joinToString(",")
        )
    }

    /** Result of one port probe; service/confirmed only apply when open. */
    data class ProbeResult(
        val port: Int,
        val open: Boolean,
        val ms: Long,
        val service: String? = null,
        val confirmed: Boolean = false
    )

    private const val BANNER_WAIT_MS = 500
    private const val REPLY_WAIT_MS = 400
    private const val TLS_WAIT_MS = 500
    private const val HTTP_PROBE = "GET / HTTP/1.1\r\nHost: scan\r\nConnection: close\r\n\r\n"

    /**
     * Parse a port list like "22,80,8000-8010" (spaces, commas, semicolons or
     * new lines separate entries, "-" marks an inclusive range, reversed
     * ranges are tolerated). Invalid tokens are ignored.
     * Falls back to defaults if nothing valid.
     */
    fun parsePorts(raw: String): List<Int> {
        val out = mutableSetOf<Int>()
        for (token in raw.split(Regex("[\\s,;]+"))) {
            if (token.isBlank()) continue
            val dash = token.split("-")
            if (dash.size == 2) {
                val a = dash[0].toIntOrNull()
                val b = dash[1].toIntOrNull()
                if (a != null && b != null) {
                    val lo = minOf(a, b).coerceIn(1, 65535)
                    val hi = maxOf(a, b).coerceIn(1, 65535)
                    for (p in lo..hi) out.add(p)
                    continue
                }
            }
            token.toIntOrNull()?.let { if (it in 1..65535) out.add(it) }
        }
        return out.sorted().ifEmpty { commonPorts }
    }

    /** Identify the service behind bytes the peer sent us (passive banner or
     *  the reply to our HTTP probe). null = not recognized. */
    internal fun fingerprint(b: ByteArray): String? {
        if (b.isEmpty()) return null
        val head = String(b, 0, minOf(b.size, 64), Charsets.ISO_8859_1)
        if (head.startsWith("SSH-")) return "ssh"
        if (head.startsWith("HTTP/")) return "http"
        if (head.startsWith("+OK")) return "pop3"
        if (head.startsWith("* OK")) return "imap"
        if (head.startsWith("RFB ")) return "vnc"
        // TLS record: ServerHello, or an alert in answer to our plaintext probe
        if (b.size > 1 && b[1] == 0x03.toByte() &&
            (b[0] == 0x16.toByte() || b[0] == 0x15.toByte())
        ) return "https"
        // MySQL/MariaDB greeting: 4-byte payload length, protocol 10, ASCII version
        if (b.size > 5 && b[4] == 0x0a.toByte() && b[5].toInt() in 0x30..0x39) return "mysql"
        if (head.startsWith("220")) {
            val up = head.uppercase()
            return when {
                "SMTP" in up -> "smtp" // covers ESMTP too
                "FTP" in up -> "ftp"
                else -> null // ambiguous 220: ftp and smtp both start with it
            }
        }
        return null
    }

    /** The OPEN line: port right-aligned in a 5-wide field (so `21/tcp`
     *  lines up under `110/tcp` like `021/tcp`, minus the zeros), `ms`
     *  right-aligned too; ✓ = seen on the wire, ? = well-known-port
     *  fallback, no label = neither. */
    internal fun openLine(port: Int, ms: Long?, service: String?, confirmed: Boolean): String {
        val time = if (ms == null) "" else "$ms ms"
        val info = when {
            service == null -> ""
            confirmed -> "$service ✓"
            else -> "$service ?"
        }
        return ("OPEN   " + portCol(port) + " " + time.padStart(8) + "  " + info).trimEnd()
    }

    /** Same port column as OPEN, so closed rows stay aligned with open ones. */
    internal fun closedLine(port: Int) = "closed " + portCol(port)

    /** Manual re-sort of a finished scan: OPEN lines on top, then closed
     *  (each group keeps its original port order); leading header comments
     *  stay on top, progress comments and the Done line sink to the bottom. */
    fun sortLinesOpenFirst(lines: List<String>): List<String> {
        val first = lines.indexOfFirst { it.startsWith("OPEN ") || it.startsWith("closed ") }
        if (first < 0) return lines
        val head = lines.subList(0, first)
        val body = lines.drop(first)
        val opens = body.filter { it.startsWith("OPEN ") }
        val closes = body.filter { it.startsWith("closed ") }
        val rest = body.filterNot { it.startsWith("OPEN ") || it.startsWith("closed ") }
        return head + opens + closes + rest
    }

    private fun portCol(port: Int) = port.toString().padStart(5) + "/tcp"

    /** Quick service test on a freshly accepted connection. Three stages,
     *  each isolated so a silent port falls through to the next one:
     *  passive banner (SSH/FTP/SMTP/POP/IMAP/MySQL speak first), one tiny
     *  HTTP request (HTTP servers answer themselves, TLS-only ports answer
     *  with a TLS record), then a real TLS handshake for ports that dropped
     *  the plaintext bytes. null = honestly unknown -> port-map fallback. */
    private fun detectService(s: Socket, host: String): String? {
        val input = runCatching { s.getInputStream() }.getOrNull() ?: return null
        val banner = try {
            s.soTimeout = BANNER_WAIT_MS
            readSome(input)
        } catch (_: Exception) {
            null // silent (HTTP/TLS ports wait for us) — try talking first
        }
        if (banner != null) return fingerprint(banner)
        val reply = try {
            s.getOutputStream().write(HTTP_PROBE.toByteArray(Charsets.US_ASCII))
            s.getOutputStream().flush()
            s.soTimeout = REPLY_WAIT_MS
            readSome(input)
        } catch (_: Exception) {
            null
        }
        if (reply != null) return fingerprint(reply) // spoke: named or honestly unknown
        // Definitive last resort: do an actual TLS handshake (SNI = host).
        // Success can only mean a TLS service -> https.
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(s, host, s.port, false) as SSLSocket
            ssl.soTimeout = TLS_WAIT_MS
            ssl.startHandshake()
            "https"
        } catch (_: Exception) {
            null
        }
    }

    private fun readSome(input: InputStream): ByteArray? {
        val buf = ByteArray(64)
        val n = input.read(buf)
        return if (n <= 0) null else buf.copyOf(n)
    }

    private fun probe(host: String, port: Int, timeoutMs: Int): ProbeResult {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                val wire = detectService(s, host)
                val ms = System.currentTimeMillis() - start
                val known = serviceByPort[port]
                // a generic protocol hit yields to a more specific port name
                // (ProxySQL 6033 speaks the MySQL protocol, its greeting counts
                // as confirmed but the label must stay proxysql)
                val named = when {
                    wire == null -> known
                    wire == "mysql" && known != null && known != "mysql" -> known
                    else -> wire
                }
                ProbeResult(port, true, ms, named, confirmed = wire != null)
            }
        } catch (_: Exception) {
            ProbeResult(port, false, System.currentTimeMillis() - start)
        }
    }

    /** Internal stream event: a finished probe vs a comment line. */
    private sealed interface Evt {
        data class Msg(val s: String) : Evt
        data class Res(val r: ProbeResult) : Evt
    }

    /** Scan list of ports on [host] as a Flow that emits **live, one line
     *  per result, still in sorted port order**: everything probes in
     *  parallel (up to 512 concurrent sockets) and a line is shown as soon
     *  as every smaller port has finished — the wait for the first line is
     *  only the smallest port's probe, not the whole scan. Huge scans
     *  (>1000 ports) only list open lines plus progress comments. */
    fun check(host: String, singlePort: Int?, timeoutMs: Int, ports: List<Int> = commonPorts): Flow<String> = flow {
        val limited = ports.take(MAX_PORTS)
        val portList = if (singlePort != null) listOf(singlePort) else limited
        emit(";; checking ${portList.size} TCP ports on $host (timeout ${timeoutMs}ms)")
        if (singlePort == null && ports.size > limited.size) {
            emit(";; note: list capped at $MAX_PORTS ports")
        }
        val openOnly = portList.size > 1000
        if (openOnly) emit(";; full scan: only open ports are listed (closed lines skipped)")
        emit("")
        val order = portList.sorted()
        val gate = Semaphore(512)
        val checked = AtomicInteger(0)
        val ch = Channel<Evt>(Channel.UNLIMITED)
        var open = 0
        coroutineScope {
            launch(Dispatchers.IO) {
                try {
                    portList.map { p ->
                        async {
                            val r = gate.withPermit { probe(host, p, timeoutMs) }
                            ch.send(Evt.Res(r))
                            val k = checked.incrementAndGet()
                            if (openOnly && k % 2048 == 0) ch.send(Evt.Msg(";; ... $k/${portList.size} ports checked"))
                        }
                    }.joinAll()
                } finally {
                    ch.close()
                }
            }
            // sorted streaming: show each result once all smaller ports arrived
            val pending = HashMap<Int, ProbeResult>()
            var next = 0
            for (evt in ch) {
                when (evt) {
                    is Evt.Msg -> emit(evt.s)
                    is Evt.Res -> {
                        pending[evt.r.port] = evt.r
                        while (next < order.size && order[next] in pending) {
                            val r = pending.remove(order[next])!!
                            next++
                            if (r.open) {
                                open++
                                emit(openLine(r.port, r.ms, r.service, r.confirmed))
                            } else if (!openOnly) {
                                emit(closedLine(r.port))
                            }
                        }
                    }
                }
            }
        }
        emit("\nDone: $open/${portList.size} ports open.")
    }.flowOn(Dispatchers.IO)
}
