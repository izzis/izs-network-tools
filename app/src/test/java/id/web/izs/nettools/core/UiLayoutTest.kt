package id.web.izs.nettools.core

import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.UiLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLayoutTest {

    @Test
    fun defaultSettingsMatchSections() {
        val s = AppSettings()
        assertEquals(UiLayout.SECTIONS, s.uiSections)
        assertFalse(s.topBarBottom)
        assertTrue(s.runRowTop)
    }

    @Test
    fun sanitizeKeepsValidOrder() {
        val custom = listOf("terminal", "target", "tools")
        assertEquals(custom, UiLayout.sanitizeSections(custom))
    }

    @Test
    fun sanitizeDropsUnknownAndAppendsMissing() {
        assertEquals(
            listOf("target", "terminal", "tools"),
            UiLayout.sanitizeSections(listOf("target", "bogus", "topbar", "terminal"))
        )
    }

    @Test
    fun sanitizeEmptyFallsBackToDefault() {
        assertEquals(UiLayout.SECTIONS, UiLayout.sanitizeSections(emptyList()))
        assertEquals(UiLayout.SECTIONS, UiLayout.sanitizeSections(listOf("nope")))
    }

    @Test
    fun sanitizeDeduplicates() {
        assertEquals(
            listOf("target", "tools", "terminal"),
            UiLayout.sanitizeSections(listOf("target", "target", "tools", "terminal", "tools"))
        )
    }

    @Test
    fun defaultDescPositionIsBottom() {
        assertEquals("bottom", AppSettings().toolDescPos)
        assertEquals("bottom", UiLayout.sanitizeDesc(null))
        assertEquals("bottom", UiLayout.sanitizeDesc("bogus"))
    }

    @Test
    fun sanitizeDescKeepsValidPositions() {
        assertEquals("top", UiLayout.sanitizeDesc("top"))
        assertEquals("bottom", UiLayout.sanitizeDesc("bottom"))
        assertEquals("hide", UiLayout.sanitizeDesc("hide"))
    }

    @Test
    fun defaultExtraPositionIsBottom() {
        assertEquals("bottom", AppSettings().toolExtraPos)
        assertEquals("bottom", UiLayout.sanitizeExtra(null))
        assertEquals("bottom", UiLayout.sanitizeExtra("hide"))
        assertEquals("top", UiLayout.sanitizeExtra("top"))
        assertEquals("bottom", UiLayout.sanitizeExtra("bottom"))
    }

    @Test
    fun defaultHeaderPositionIsTop() {
        assertEquals("top", AppSettings().toolExtraHeader)
        assertEquals("top", UiLayout.sanitizeHeader(null))
        assertEquals("top", UiLayout.sanitizeHeader("hide"))
        assertEquals("bottom", UiLayout.sanitizeHeader("bottom"))
    }
}
