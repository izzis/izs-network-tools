package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.xbill.DNS.Lookup
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.net.InetAddress

/** DNS lookup via dnsjava against a configurable server. */
object DnsRunner {
    val types = listOf("A", "AAAA", "MX", "TXT", "NS", "CNAME", "SOA")

    /** Fixed answer columns so every record type lines up on a phone-width
     *  console: the value starts at col 28, so an A row still fits one line.
     *  Each field carries its own NBSP before padEnd - padEnd alone leaves zero
     *  separator when the value is exactly the field width ("3600IN MX"). NBSP
     *  is glue, so padding never becomes a break point; the single real space
     *  before the value is the only break point, and a long row wraps flush-left
     *  with the value alone on line 2. */
    private fun answerRow(name: String, ttl: String, type: String, rdata: String): String =
        (name + '\u00A0').padEnd(16, '\u00A0') + (ttl + '\u00A0').padEnd(4, '\u00A0') +
            "IN\u00A0" + (type + '\u00A0').padEnd(4, '\u00A0') + " " + rdata

    private fun typeInt(name: String): Int = when (name.uppercase()) {
        "A" -> Type.A
        "AAAA" -> Type.AAAA
        "MX" -> Type.MX
        "TXT" -> Type.TXT
        "NS" -> Type.NS
        "CNAME" -> Type.CNAME
        "SOA" -> Type.SOA
        else -> Type.A
    }

    fun lookup(host: String, typeName: String, server: String, timeoutMs: Int): Flow<String> = flow {
        emit("; DiG via $server $host $typeName\n")
        val result: Array<org.xbill.DNS.Record>? = try {
            val lookup = Lookup(host.trimEnd('.') + ".", typeInt(typeName))
            lookup.setResolver(SimpleResolver(server))
            lookup.run()
        } catch (e: Exception) {
            emit(";; error: ${e.message}")
            return@flow
        }
        if (result == null) {
            // Fallback to system resolver for A/AAAA so common case still works
            if (typeName == "A" || typeName == "AAAA") {
                try {
                    val addrs = InetAddress.getAllByName(host)
                    emit(";; (system resolver fallback)")
                    addrs.forEach { emit(answerRow("$host.", "-", typeName, it.hostAddress)) }
                } catch (e: Exception) {
                    emit(";; NXDOMAIN / not found: ${e.message}")
                }
            } else {
                emit(";; no $typeName record for $host")
            }
            return@flow
        }
        emit(";; ANSWER SECTION (${result.size}):")
        result.forEach { r ->
            emit(answerRow(r.name.toString(), r.ttl.toString(), Type.string(r.type), r.rdataToString()))
        }
    }.flowOn(Dispatchers.IO)
}
