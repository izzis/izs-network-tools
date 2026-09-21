package id.web.izs.nettools.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.net.InetSocketAddress
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
 * target like "example.com:8443"), performs a handshake with SNI, and prints
 * the presented chain: subject, issuer, validity, SANs, SHA-256 fingerprint.
 *
 * NOTE: never emit() from inside withContext() here — the flow already runs
 * on Dispatchers.IO via flowOn, and emitting from another context violates
 * Flow exception transparency.
 */
object CertChecker {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /** Trust manager that accepts everything but records the presented chain. */
    private class CaptureTM : X509TrustManager {
        var chain: Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(c: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(c: Array<X509Certificate>, authType: String) {
            chain = c
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

    private fun openSocket(
        host: String,
        port: Int,
        timeoutMs: Int,
        factory: SSLSocketFactory
    ): SSLSocket {
        val raw = factory.createSocket()
        raw.connect(InetSocketAddress(host, port), timeoutMs)
        val sock = raw as SSLSocket
        // SNI only makes sense for DNS names, not IP literals.
        if (!TargetParser.isIp(host)) {
            try {
                val params = sock.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                sock.sslParameters = params
            } catch (_: Exception) {
            }
        }
        sock.soTimeout = timeoutMs
        return sock
    }

    fun fetch(host: String, port: Int, timeoutMs: Int): Flow<String> = flow {
        emit(";; TLS certificate for $host:$port\n")
        // 1) Capture the chain without validating, so even self-signed
        //    certificates can be displayed.
        val capture = CaptureTM()
        var protocol = ""
        var cipher = ""
        try {
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(capture), SecureRandom())
            openSocket(host, port, timeoutMs, ctx.socketFactory as SSLSocketFactory).use { sock ->
                sock.startHandshake()
                val session = sock.session
                protocol = session.protocol
                cipher = session.cipherSuite
            }
        } catch (e: Exception) {
            emit("ERROR: TLS handshake failed: ${e.message}")
            emit("Hint: the port may not speak implicit TLS (SMTP/IMAP need STARTTLS), or the host is unreachable.")
            return@flow
        }
        val chain = capture.chain
        if (chain.isEmpty()) {
            emit("ERROR: server presented no certificates.")
            return@flow
        }
        emit("Protocol: $protocol, Cipher: $cipher")
        // 2) Verify trust against the system store with a second handshake.
        val trusted = try {
            openSocket(host, port, timeoutMs, SSLSocketFactory.getDefault() as SSLSocketFactory).use { sock ->
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
