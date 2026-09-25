package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OuiDbTest {

    private val table = OuiDb.parse(
        "001bc5\tParent Co\n" +          // 24-bit MA-L parent
            "001bc5000\tChild Inc\n" +    // 36-bit MA-S under the parent
            "001bc50\tMid Ltd\n" +        // 28-bit MA-M under the parent
            "0015eb\tZTE\n" +
            "badline\n" +                 // no tab → skipped
            "\tNoVendor\n" +              // empty prefix → skipped
            "abcd\t"                      // empty vendor → skipped
    )

    @Test
    fun parseSplitsPrefixAndVendorAndSkipsMalformedLines() {
        assertEquals(4, table.size)
        assertEquals("ZTE", table["0015eb"])
        assertEquals("Parent Co", table["001bc5"])
    }

    @Test
    fun lookupPrefersTheLongestMatchingPrefix() {
        assertEquals("Child Inc", OuiDb.lookup(table, "00:1b:c5:00:00:11"))
        assertEquals("Mid Ltd", OuiDb.lookup(table, "00:1b:c5:00:22:33"))
        assertEquals("Parent Co", OuiDb.lookup(table, "00:1b:c5:aa:bb:cc"))
    }

    @Test
    fun lookupAcceptsAnyMacFormatAndCase() {
        assertEquals("ZTE", OuiDb.lookup(table, "00-15-EB-11-22-33"))
        assertEquals("ZTE", OuiDb.lookup(table, "0015eb112233"))
        assertEquals("ZTE", OuiDb.lookup(table, "00:15:eb:11:22:33"))
    }

    @Test
    fun lookupMissOrShortInputReturnsEmpty() {
        assertEquals("", OuiDb.lookup(table, "ff:ff:ff:ff:ff:ff"))
        assertEquals("", OuiDb.lookup(table, ""))
        assertEquals("", OuiDb.lookup(table, "zz:zz:zz"))
    }

    @Test
    fun vendorColumnIsCappedAt18Chars() {
        // Mirrors scripts/gen_oui.py: names are truncated to the 18-col column.
        val long = OuiDb.parse("aabbcc\tInternational Busi\n")
        assertTrue(long.getValue("aabbcc").length <= 18)
        assertEquals("International Busi", long["aabbcc"])
    }
}
