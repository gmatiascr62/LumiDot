package com.lumidot.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedSettingsTest {

    @Test
    fun patternsStayInRange() {
        BlinkPattern.entries.forEach { p ->
            for (i in 0..1000) {
                val v = p.intensity(i / 1000f)
                assertTrue("$p fuera de rango en $i: $v", v in 0f..1f)
            }
        }
    }

    @Test
    fun solidIsAlwaysOn() {
        assertEquals(1f, BlinkPattern.SOLID.intensity(0.37f))
    }

    @Test
    fun quietHoursAcrossMidnight() {
        val s = LedSettings(quietHoursEnabled = true, quietStart = 23 * 60, quietEnd = 7 * 60)
        assertTrue(s.isInQuietHours(23 * 60 + 30))
        assertTrue(s.isInQuietHours(3 * 60))
        assertFalse(s.isInQuietHours(12 * 60))
        assertFalse(s.copy(quietHoursEnabled = false).isInQuietHours(3 * 60))
    }

    @Test
    fun appFilterModes() {
        val rules = mapOf("a" to AppRule("a", enabled = false), "b" to AppRule("b", enabled = true, color = 1))
        val all = LedSettings(appRules = rules)
        assertFalse(all.isAppAllowed("a"))
        assertTrue(all.isAppAllowed("b"))
        assertTrue(all.isAppAllowed("c"))
        val only = all.copy(filterMode = AppFilterMode.ONLY_SELECTED)
        assertFalse(only.isAppAllowed("a"))
        assertTrue(only.isAppAllowed("b"))
        assertFalse(only.isAppAllowed("c"))
        assertEquals(1, all.colorFor("b"))
        assertEquals(all.color, all.colorFor("c"))
    }
}
