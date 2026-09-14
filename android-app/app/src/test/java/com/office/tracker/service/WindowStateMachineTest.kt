package com.office.tracker.service

import com.office.tracker.db.OfficeVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowStateMachineTest {

    private val sm = WindowStateMachine(debounceCount = 3)
    private val noVisit: OfficeVisit? = null
    private val now = 1_000_000L

    private fun visit(arrived: Boolean = false, atOffice: Boolean = false): OfficeVisit? =
        if (arrived) OfficeVisit(
            date = "2026-09-14",
            arrivalTime = "09:30",
            arrivalTimestamp = 500_000L,
            isCurrentlyAtOffice = atOffice
        ) else null

    @Test
    fun firstArrivalFixLogsArrival() {
        val (state, event) = sm.onLocation(WindowType.ARRIVAL, isAtOffice = true, noVisit, WindowState(), now)
        assertTrue(state.hasLoggedArrival)
        assertEquals(WindowEvent.LogArrival(now), event)
    }

    @Test
    fun repeatedArrivalFixIsIdempotent() {
        val (state, _) = sm.onLocation(WindowType.ARRIVAL, true, noVisit, WindowState(), now)
        val (state2, event2) = sm.onLocation(WindowType.ARRIVAL, true, noVisit, state, now)
        assertEquals(WindowEvent.Noop, event2)
    }

    @Test
    fun leavingDuringArrivalWindowLogsDeparture() {
        val state = WindowState(hasLoggedArrival = true)
        val (_, event) = sm.onLocation(WindowType.ARRIVAL, isAtOffice = false, noVisit, state, now)
        assertEquals(WindowEvent.LogDeparture(now), event)
    }

    @Test
    fun departureNeedsDebounceAndCurrentAtOffice() {
        // Not at office in DB -> no departure even after 3 outside fixes.
        var state = WindowState()
        val event = sm.onLocation(WindowType.DEPARTURE, false, noVisit, state, now).second
        repeat(2) { state = sm.onLocation(WindowType.DEPARTURE, false, noVisit, state, now).first }
        assertEquals(WindowEvent.Noop, event)

        val v = visit(arrived = true, atOffice = true)
        var s2 = WindowState()
        repeat(2) { s2 = sm.onLocation(WindowType.DEPARTURE, false, v, s2, now).first }
        val (finalState, finalEvent) = sm.onLocation(WindowType.DEPARTURE, false, v, s2, now)
        assertEquals(WindowEvent.LogDeparture(now), finalEvent)
        assertTrue(finalState.hasLoggedDeparture)
    }

    @Test
    fun returningAfterDepartureLogsReArrival() {
        val v = visit(arrived = true, atOffice = true)
        val state = WindowState(hasLoggedArrival = true, hasLoggedDeparture = true)
        val (newState, event) = sm.onLocation(WindowType.DEPARTURE, true, v, state, now)
        assertEquals(WindowEvent.ReArrival(now), event)
        assertTrue(newState.hasLoggedArrival)
        assertEquals(0, newState.outsideCount)
    }
}
