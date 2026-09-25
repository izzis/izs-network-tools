package id.web.izs.nettools.core

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertCheckerTest {

    @Test
    fun modesForTriesUpgradeThenImplicit() {
        assertEquals(
            listOf(CertChecker.Mode.SMTP, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(25)
        )
        assertEquals(
            listOf(CertChecker.Mode.SMTP, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(587)
        )
        assertEquals(
            listOf(CertChecker.Mode.IMAP, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(143)
        )
        assertEquals(
            listOf(CertChecker.Mode.POP3, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(110)
        )
        assertEquals(
            listOf(CertChecker.Mode.FTP, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(21)
        )
        assertEquals(
            listOf(CertChecker.Mode.MYSQL, CertChecker.Mode.IMPLICIT),
            CertChecker.modesFor(3306)
        )
    }

    @Test
    fun modesForUnknownPortIsImplicitOnly() {
        assertEquals(listOf(CertChecker.Mode.IMPLICIT), CertChecker.modesFor(443))
        assertEquals(listOf(CertChecker.Mode.IMPLICIT), CertChecker.modesFor(8443))
        assertEquals(listOf(CertChecker.Mode.IMPLICIT), CertChecker.modesFor(993))
    }

    @Test
    fun smtpEhloDoneOnFinalLineOnly() {
        assertFalse(CertChecker.smtpEhloDone("250-STARTTLS"))
        assertFalse(CertChecker.smtpEhloDone("250-PIPELINING"))
        assertTrue(CertChecker.smtpEhloDone("250 PIPELINING"))
        assertTrue(CertChecker.smtpEhloDone("250 OK"))
        // lenient servers that skip the space
        assertTrue(CertChecker.smtpEhloDone("250OK"))
    }

    @Test
    fun smtpOffersStarttlsMatchesCapability() {
        assertTrue(
            CertChecker.smtpOffersStarttls(
                listOf("250-PIPELINING", "250 STARTTLS")
            )
        )
        assertTrue(
            CertChecker.smtpOffersStarttls(listOf("250 starttls"))
        )
        assertFalse(
            CertChecker.smtpOffersStarttls(
                listOf("250-PIPELINING", "250 SIZE 35882577")
            )
        )
    }

    /** Classic greeting: protocol 10, version, thread id, auth data,
     *  filler, capability word (CLIENT_SSL set). */
    private fun greeting(caps: Int, includeCaps: Boolean = true): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(10) // protocol version
        out.write("8.0.36".toByteArray()) // server version
        out.write(0) // version NUL
        out.write(byteArrayOf(1, 2, 3, 4)) // thread id
        out.write(ByteArray(8) { 0x41 }) // auth-plugin-data-part-1
        out.write(0) // filler
        if (includeCaps) {
            out.write(caps and 0xFF)
            out.write((caps shr 8) and 0xFF)
        }
        return out.toByteArray()
    }

    @Test
    fun parseMysqlCapabilitiesReadsLowWord() {
        val caps = CertChecker.parseMysqlCapabilities(
            greeting(0x8A01 or CertChecker.CLIENT_SSL)
        )
        assertTrue(caps and CertChecker.CLIENT_SSL != 0)
        assertTrue(caps and 0x0001 != 0) // LONG_PASSWORD survived the offset math
        val noTls = CertChecker.parseMysqlCapabilities(greeting(0x0001))
        assertEquals(0, noTls and CertChecker.CLIENT_SSL)
    }

    @Test
    fun parseMysqlCapabilitiesRejectsShortGreeting() {
        try {
            CertChecker.parseMysqlCapabilities(byteArrayOf(10, 8, 46, 48))
            throw AssertionError("expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun mysqlSslRequestIs32BytesWithClientSsl() {
        val p = CertChecker.buildMysqlSslRequest()
        assertEquals(32, p.size)
        val caps = (p[0].toInt() and 0xFF) or
            ((p[1].toInt() and 0xFF) shl 8) or
            ((p[2].toInt() and 0xFF) shl 16) or
            ((p[3].toInt() and 0xFF) shl 24)
        assertEquals(0x0001 or 0x0200 or CertChecker.CLIENT_SSL or 0x8000, caps)
        assertTrue(caps and CertChecker.CLIENT_SSL != 0)
        // max packet 0x01000000 LE at [4..7]
        assertEquals(0, p[4].toInt())
        assertEquals(0, p[5].toInt())
        assertEquals(0, p[6].toInt())
        assertEquals(1, p[7].toInt())
        assertEquals(45, p[8].toInt()) // utf8mb4 charset
        // 23 reserved bytes stay zero
        for (i in 9 until 32) assertEquals(0, p[i].toInt())
    }
}
