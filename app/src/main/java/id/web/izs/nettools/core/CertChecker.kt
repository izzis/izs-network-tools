package id.web.izs.nettools.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * TLS certificate viewer. Connects to host:port (default 443, parsed from
 * target like "example.com:25") and prints the presented chain: subject,
 * issuer, validity, SANs, SHA-256 fingerprint.
 *
 * TLS arrives three ways — [Mode.IMPLICIT] (TLS from the first byte, works
 * on any port), a plaintext upgrade dialogue ([Mode.SMTP]/[Mode.IMAP]/
 * [Mode.POP3]/[Mode.FTP] on their classic ports), and MySQL's in-band
 * SSLRequest ([Mode.MYSQL], port 3306: greeting, then a 32-byte capability
 * packet with CLIENT_SSL, then TLS on the same socket). [modesFor] lists
 * the order per port — upgrade first, implicit TLS as the fallback — and
 * the first handshake that completes wins; the system-store trust re-check
 * repeats the same path.
 *
 * NOTE: never emit() from inside withContext() here — the flow already runs
 * on Dispatchers.IO via flowOn, and emitting from another context violates
 * Flow exception transparency.
 */
object CertChecker {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /** How TLS gets established on a port. */
    enum class Mode(val label: String) {
        IMPLICIT("implicit TLS"),
        SMTP("STARTTLS (smtp)"),
        IMAP("STARTTLS (imap)"),
        POP3("STARTTLS (pop3)"),
        FTP("AUTH TLS (ftp)"),
        MYSQL("mysql SSL upgrade")
    }

    /** CLIENT_SSL capability bit — MySQL will switch to TLS after the
     *  32-byte SSLRequest when the client sets it. */
    const val CLIENT_SSL = 0x0800

    /** Upgrade order for a port: the plaintext-dialogue upgrade first,
     *  implicit TLS as fallback. Unknown ports = implicit only. */
    fun modesFor(port: Int): List<Mode> = when (port) {
        25, 587 -> listOf(Mode.SMTP, Mode.IMPLICIT)
        143 -> listOf(Mode.IMAP, Mode.IMPLICIT)
        110 -> listOf(Mode.POP3, Mode.IMPLICIT)
        21 -> listOf(Mode.FTP, Mode.IMPLICIT)
        3306 -> listOf(Mode.MYSQL, Mode.IMPLICIT)
        else -> listOf(Mode.IMPLICIT)
    }

    /** Trust manager that accepts everything but records the presented chain.
     *
     *  Suppression is deliberate, not lazy: the cert *inspector* must print
     *  the chain a server actually presents, expired/self-signed included —
     *  that IS the feature. Scoped to this probe socket only; app traffic
     *  stays on the platform's default trust manager.
     */
    @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
    private class CaptureTM : X509TrustManager {
        var chain: Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            this.chain = chain
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun formatCert(index: Int, cert: X509Certificate): List<String> {
        val out = mutableListOf<String>()
        val role = if (index == 0) "leaf" else "chain[$index]"
        out.add("-- Certificate ($role) --")
        out.add("Subject: ${cert.subjectX500Principal.name}")
        out.add("Issuer: ${cert.issuerX500Principal.name}")
        out.add("Serial: ${cert.serialNumber.toString(16).uppercase()}")
        val now = Date().time
        val daysLeft = (cert.notAfter.time - now) / 86_400_000L
        val status = when {
            now < cert.notBefore.time -> "NOT YET VALID"
            now > cert.notAfter.time -> "EXPIRED"
            else -> "valid, expires in $daysLeft day(s)"
        }
        out.add("Valid: ${dateFmt.format(cert.notBefore)} -> ${dateFmt.format(cert.notAfter)} ($status)")
        out.add("Signature: ${cert.sigAlgName}")
        try {
            val sans = cert.subjectAlternativeNames
                ?.mapNotNull { it.getOrNull(1)?.toString() }
                ?.distinct()
            if (!sans.isNullOrEmpty()) out.add("SANs: ${sans.joinToString(", ")}")
        } catch (_: Exception) {
        }
        out.add("SHA-256: ${fingerprint(cert)}")
        return out
    }

    private fun applySni(sock: SSLSocket, host: String) {
        // SNI only makes sense for DNS names, not IP literals.
        if (TargetParser.isIp(host)) return
        try {
            val params = sock.sslParameters
            params.serverNames = listOf(SNIHostName(host))
            sock.sslParameters = params
        } catch (_: Exception) {
        }
    }

    /**
     * Open a socket ready for startHandshake() in [mode]. [Mode.IMPLICIT]
     * connects the TLS socket directly; the upgrade modes speak a short
     * plaintext dialogue first and then layer the factory's TLS socket over
     * the SAME connection (autoClose so one close tears both down).
     */
    private fun openSocket(
        host: String,
        port: Int,
        timeoutMs: Int,
        factory: SSLSocketFactory,
        mode: Mode
    ): SSLSocket {
        val raw: Socket = if (mode == Mode.IMPLICIT) factory.createSocket() else Socket()
        try {
            raw.connect(InetSocketAddress(host, port), timeoutMs)
            raw.soTimeout = timeoutMs
            if (mode == Mode.IMPLICIT) {
                val ssl = raw as SSLSocket
                applySni(ssl, host)
                return ssl
            }
            startTlsUpgrade(raw, mode)
            val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
            applySni(ssl, host)
            return ssl
        } catch (e: Exception) {
            try {
                raw.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    /** Plaintext dialogue that ends with "upgrade to TLS now". */
    private fun startTlsUpgrade(raw: Socket, mode: Mode) {
        val input = raw.getInputStream()
        val output = raw.getOutputStream()
        when (mode) {
            Mode.SMTP -> {
                readReply220(input, "SMTP")
                writeLine(output, "EHLO nettools.local")
                val lines = mutableListOf<String>()
                while (true) {
                    val l = readLine(input)
                    lines += l
                    if (smtpEhloDone(l)) break
                    if (l.startsWith("5")) throw IOException("EHLO failed: $l")
                }
                if (!smtpOffersStarttls(lines)) {
                    throw IOException("server does not advertise STARTTLS")
                }
                writeLine(output, "STARTTLS")
                val ok = readLine(input)
                if (!ok.startsWith("220 ")) throw IOException("STARTTLS refused: $ok")
            }
            Mode.IMAP -> {
                val greet = readLine(input)
                if (!greet.startsWith("* OK")) throw IOException("IMAP greeting not OK: $greet")
                writeLine(output, "a001 STARTTLS")
                var reply: String
                do {
                    reply = readLine(input)
                } while (!reply.startsWith("a001 "))
                if (!reply.startsWith("a001 OK")) throw IOException("STARTTLS refused: $reply")
            }
            Mode.POP3 -> {
                val greet = readLine(input)
                if (!greet.startsWith("+OK")) throw IOException("POP3 greeting not +OK: $greet")
                writeLine(output, "STLS")
                val ok = readLine(input)
                if (!ok.startsWith("+OK")) throw IOException("STLS refused: $ok")
            }
            Mode.FTP -> {
                readReply220(input, "FTP")
                writeLine(output, "AUTH TLS")
                val ok = readLine(input)
                // RFC 4217 answers 234; some daemons answer 334.
                if (!ok.startsWith("234") && !ok.startsWith("334")) {
                    throw IOException("AUTH TLS refused: $ok")
                }
            }
            Mode.MYSQL -> mysqlSslRequest(input, output)
            Mode.IMPLICIT -> Unit
        }
    }

    /** Greeting loop that accepts RFC 5321/959 multiline "220-" banners. */
    private fun readReply220(input: InputStream, proto: String): String {
        var line = readLine(input)
        while (line.startsWith("220-")) line = readLine(input)
        if (!line.startsWith("220 ")) throw IOException("$proto greeting not 220: $line")
        return line
    }

    /** True once an EHLO multiline reply reaches its final "250" line. */
    internal fun smtpEhloDone(line: String): Boolean =
        line.startsWith("250 ") || (line.startsWith("250") && !line.startsWith("250-"))

    /** STARTTLS offered anywhere in the EHLO capability lines. */
    internal fun smtpOffersStarttls(lines: List<String>): Boolean =
        lines.any { it.contains("STARTTLS", ignoreCase = true) }

    /**
     * MySQL in-band TLS: read the server greeting, verify CLIENT_SSL, then
     * answer with the 32-byte SSLRequest packet — after which the server
     * expects a TLS ClientHello on the same socket (no more plaintext).
     */
    private fun mysqlSslRequest(input: InputStream, output: OutputStream) {
        val header = readFully(input, 4)
        val len = (header[0].toInt() and 0xFF) or
            ((header[1].toInt() and 0xFF) shl 8) or
            ((header[2].toInt() and 0xFF) shl 16)
        if (len <= 0 || len > 4096) throw IOException("bad MySQL greeting length $len")
        val payload = readFully(input, len)
        if (parseMysqlCapabilities(payload) and CLIENT_SSL == 0) {
            throw IOException("MySQL server does not offer TLS (CLIENT_SSL off)")
        }
        // Packet header: payload length (3, LE) + sequence 1 (greeting was 0).
        output.write(byteArrayOf(32, 0, 0, 1) + buildMysqlSslRequest())
        output.flush()
    }

    /**
     * Lower 16 capability bits from a classic-protocol greeting payload:
     * [0] protocol version, server version\0, thread id(4),
     * auth-plugin-data-part-1(8), filler(1), then the capability word —
     * CLIENT_SSL (0x0800) lives in these low bits.
     */
    internal fun parseMysqlCapabilities(payload: ByteArray): Int {
        var i = 1 // skip protocol version
        while (i < payload.size && payload[i].toInt() != 0) i++ // server version
        i += 1 + 4 + 8 + 1 // NUL + thread id + auth data + filler
        if (i + 1 >= payload.size) throw IOException("short MySQL greeting")
        return (payload[i].toInt() and 0xFF) or ((payload[i + 1].toInt() and 0xFF) shl 8)
    }

    /**
     * 32-byte MySQL SSLRequest payload: capability flags (LONG_PASSWORD |
     * PROTOCOL_41 | CLIENT_SSL | SECURE_CONNECTION = 0x8A01), 16 MiB max
     * packet, charset 45 (utf8mb4), 23 reserved zero bytes.
     */
    internal fun buildMysqlSslRequest(): ByteArray {
        val caps = 0x0001 or 0x0200 or CLIENT_SSL or 0x8000
        val p = ByteArray(32)
        p[0] = (caps and 0xFF).toByte()
        p[1] = ((caps shr 8) and 0xFF).toByte()
        p[2] = ((caps shr 16) and 0xFF).toByte()
        p[3] = ((caps shr 24) and 0xFF).toByte()
        p[7] = 0x01 // max packet 0x01000000 LE
        p[8] = 45 // utf8mb4
        return p
    }

    private fun writeLine(out: OutputStream, cmd: String) {
        out.write((cmd + "\r\n").toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    /** One CRLF-terminated protocol line, capped at 4 KiB. */
    private fun readLine(input: InputStream): String {
        val out = StringBuilder()
        while (out.length < 4096) {
            val b = input.read()
            if (b == -1) throw IOException("connection closed while reading a reply")
            if (b == '\n'.code) return out.toString().trimEnd('\r')
            out.append(b.toChar())
        }
        throw IOException("reply line too long")
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r == -1) throw IOException("connection closed (got $off/$n bytes)")
            off += r
        }
        return buf
    }

    fun fetch(host: String, port: Int, timeoutMs: Int): Flow<String> = flow {
        emit(";; TLS certificate for $host:$port\n")
        // 1) Capture the chain without validating, so even self-signed
        //    certificates can be displayed. Try every mode for the port —
        //    upgrade dialogue first, implicit TLS last.
        val capture = CaptureTM()
        val captureFactory = try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(capture), SecureRandom())
            ctx.socketFactory as SSLSocketFactory
        } catch (e: Exception) {
            emit("ERROR: ${e.message}")
            return@flow
        }
        var protocol = ""
        var cipher = ""
        var mode: Mode? = null
        var lastError: String? = null
        for (m in modesFor(port)) {
            try {
                openSocket(host, port, timeoutMs, captureFactory, m).use { sock ->
                    sock.startHandshake()
                    val session = sock.session
                    protocol = session.protocol
                    cipher = session.cipherSuite
                }
                mode = m
                break
            } catch (e: Exception) {
                lastError = e.message
            }
        }
        if (mode == null) {
            emit("ERROR: TLS handshake failed: $lastError")
            val tried = modesFor(port).joinToString { it.label }
            emit("Hint: no TLS found on this port (tried: $tried), or the host is unreachable.")
            return@flow
        }
        val chain = capture.chain
        if (chain.isEmpty()) {
            emit("ERROR: server presented no certificates.")
            return@flow
        }
        emit("Mode: ${mode.label}, Protocol: $protocol, Cipher: $cipher")
        // 2) Verify trust against the system store over the same path.
        val trusted = try {
            openSocket(
                host, port, timeoutMs,
                SSLSocketFactory.getDefault() as SSLSocketFactory, mode
            ).use { sock ->
                sock.startHandshake()
            }
            true
        } catch (_: Exception) {
            false
        }
        emit(if (trusted) "Trusted: yes (system store)" else "Trusted: NO - not trusted by the system store")
        chain.forEachIndexed { i, c ->
            formatCert(i, c).forEach { emit(it) }
            if (i < chain.size - 1) emit("")
        }
    }.flowOn(Dispatchers.IO)
}
