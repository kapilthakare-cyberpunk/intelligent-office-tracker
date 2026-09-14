package com.office.tracker.service

import com.office.tracker.db.OfficeVisit

/**
 * Pure state machine for the arrival/departure window logic.
 *
 * Extracted from OfficeTrackingService so the transition rules are unit-testable
 * and survive service restarts (the [WindowState] can be rebuilt from the DB
 * visit + a persisted outside-count).
 *
 * Mirror of the original service semantics:
 *  - arrival window: first fix inside the office logs an arrival; leaving during
 *    the arrival window logs a departure.
 *  - departure window: three consecutive outside fixes log a departure (debounce);
 *    coming back after a logged departure logs a re-arrival.
 */
data class WindowState(
    val hasLoggedArrival: Boolean = false,
    val hasLoggedDeparture: Boolean = false,
    val outsideCount: Int = 0
)

sealed interface WindowEvent {
    data class LogArrival(val millis: Long) : WindowEvent
    data class LogDeparture(val millis: Long) : WindowEvent
    data class ReArrival(val millis: Long) : WindowEvent
    data object Noop : WindowEvent
}

class WindowStateMachine(private val debounceCount: Int = 3) {

    fun onLocation(
        window: WindowType,
        isAtOffice: Boolean,
        visit: OfficeVisit?,
        state: WindowState,
        nowMillis: Long
    ): Pair<WindowState, WindowEvent> {
        return when (window) {
            WindowType.ARRIVAL -> onArrivalFix(isAtOffice, visit, state, nowMillis)
            WindowType.DEPARTURE -> onDepartureFix(isAtOffice, visit, state, nowMillis)
        }
    }

    private fun onArrivalFix(
        isAtOffice: Boolean,
        visit: OfficeVisit?,
        state: WindowState,
        nowMillis: Long
    ): Pair<WindowState, WindowEvent> {
        if (isAtOffice) {
            val alreadyLogged = visit?.arrivalTimestamp != null && visit.arrivalTimestamp > 0L
            if (state.hasLoggedArrival || alreadyLogged) {
                // Idempotent: either just logged, or the DB already has it
                // (e.g. after a service restart). Just sync the state.
                return state.copy(hasLoggedArrival = true) to WindowEvent.Noop
            }
            return state.copy(hasLoggedArrival = true) to WindowEvent.LogArrival(nowMillis)
        } else {
            if (state.hasLoggedArrival) {
                // Left office during the arrival window -> departure.
                return state.copy(hasLoggedArrival = false) to WindowEvent.LogDeparture(nowMillis)
            }
            return state to WindowEvent.Noop
        }
    }

    private fun onDepartureFix(
        isAtOffice: Boolean,
        visit: OfficeVisit?,
        state: WindowState,
        nowMillis: Long
    ): Pair<WindowState, WindowEvent> {
        val currentlyAtOffice = visit?.isCurrentlyAtOffice == true
        if (!isAtOffice && !state.hasLoggedDeparture) {
            val count = state.outsideCount + 1
            if (count >= debounceCount && currentlyAtOffice) {
                return state.copy(outsideCount = 0, hasLoggedDeparture = true) to
                    WindowEvent.LogDeparture(nowMillis)
            }
            return state.copy(outsideCount = count) to WindowEvent.Noop
        } else if (isAtOffice && state.hasLoggedDeparture) {
            // Back at the office after logging a departure -> re-arrival.
            return state.copy(
                hasLoggedDeparture = false,
                hasLoggedArrival = true,
                outsideCount = 0
            ) to WindowEvent.ReArrival(nowMillis)
        } else if (isAtOffice && !state.hasLoggedDeparture) {
            return state.copy(outsideCount = 0) to WindowEvent.Noop
        }
        return state to WindowEvent.Noop
    }
}
