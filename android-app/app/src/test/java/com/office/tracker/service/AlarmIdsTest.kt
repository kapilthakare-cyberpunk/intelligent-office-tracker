package com.office.tracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmIdsTest {

    @Test
    fun consecutiveDaysUseDifferentCodesForEverySlot() {
        val today = 1_000_000L
        for (slot in intArrayOf(
            AlarmIds.SLOT_ARRIVAL_START, AlarmIds.SLOT_ARRIVAL_END,
            AlarmIds.SLOT_DEPARTURE_START, AlarmIds.SLOT_DEPARTURE_END,
            AlarmIds.SLOT_CLOCK_SHOW, AlarmIds.SLOT_CLOCK_EDIT
        )) {
            assertNotEquals(
                "slot $slot must differ between today and tomorrow",
                AlarmIds.requestCode(today, slot),
                AlarmIds.requestCode(today + 1, slot)
            )
        }
    }

    @Test
    fun slotsWithinSameDayDiffer() {
        val day = 5L
        val codes = setOf(
            AlarmIds.requestCode(day, AlarmIds.SLOT_ARRIVAL_START),
            AlarmIds.requestCode(day, AlarmIds.SLOT_ARRIVAL_END),
            AlarmIds.requestCode(day, AlarmIds.SLOT_DEPARTURE_START),
            AlarmIds.requestCode(day, AlarmIds.SLOT_DEPARTURE_END)
        )
        assertEquals(4, codes.size)
    }

    @Test
    fun staysDeterministicAndInRange() {
        for (epochDay in longArrayOf(0, 1, 20_000, 100_000, 5_000_000)) {
            for (slot in 0..9) {
                val code = AlarmIds.requestCode(epochDay, slot)
                assertTrue(code in 0..1_000_000)
                assertEquals(code, AlarmIds.requestCode(epochDay, slot))
            }
        }
    }
}
