package com.office.tracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatsTest {

    @Test
    fun converts24hStringTo12h() {
        assertEquals("2:40 pm", format12h("14:40"))
        assertEquals("10:18 am", format12h("10:18"))
        assertEquals("12:00 am", format12h("00:00"))
        assertEquals("12:30 pm", format12h("12:30"))
        assertEquals("2:40 pm", format12h("14:40:22"))
    }

    @Test
    fun keepsExplicitMeridian() {
        assertEquals("7:48 pm", format12h("7:48 pm"))
        assertEquals("10:18 am", format12h("10:18 AM"))
    }

    @Test
    fun nullOnGarbage() {
        assertNull(format12h(""))
        assertNull(format12h(null))
        assertNull(format12h("n/a"))
    }

    @Test
    fun displayPrefersMillis() {
        // 2026-09-14 09:30 local = epoch millis (device tz dependent).
        val millis = java.time.LocalDate.parse("2026-09-14")
            .atTime(9, 30)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(format12hMillis(millis), displayTime("99:99", millis))
        assertEquals("10:18 am", displayTime("10:18", 0))
    }
}
