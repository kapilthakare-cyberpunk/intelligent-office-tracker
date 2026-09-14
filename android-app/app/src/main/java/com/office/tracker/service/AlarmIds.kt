package com.office.tracker.service

/**
 * Stable, per-day alarm identity.
 *
 * AlarmManager replaces an alarm when its PendingIntent matches (request code +
 * intent action/component). The original code reused the same request codes for
 * today and tomorrow, so scheduling tomorrow silently replaced today's window
 * alarms. Deriving the request code from the target epoch-day gives every day a
 * unique identity, so today and tomorrow can coexist as independent alarms.
 */
object AlarmIds {

    /** Slot ordinals for the four window alarms for one day. */
    const val SLOT_ARRIVAL_START = 0
    const val SLOT_ARRIVAL_END = 1
    const val SLOT_DEPARTURE_START = 2
    const val SLOT_DEPARTURE_END = 3

    /** Slot ordinals for the redundant setAlarmClock PIs (arrival start). */
    const val SLOT_CLOCK_SHOW = 8
    const val SLOT_CLOCK_EDIT = 9

    /** Legacy fixed request codes used by pre-refactor builds (leave usable, we cancel them). */
    const val LEGACY_ARRIVAL_START = 0
    const val LEGACY_ARRIVAL_END = 1
    const val LEGACY_DEPARTURE_START = 2
    const val LEGACY_DEPARTURE_END = 3
    const val LEGACY_ARRIVAL_CHECK = 10
    const val LEGACY_DEPARTURE_CHECK = 11
    const val LEGACY_CLOCK_SHOW = 98
    const val LEGACY_CLOCK_EDIT = 99

    /**
     * Unique request code for [slot] on [epochDay].
     * Window spans 36525 days (~100 years); codes stay well within Int range.
     */
    fun requestCode(epochDay: Long, slot: Int): Int {
        val day = ((epochDay % 36525L) + 36525L) % 36525L
        return day.toInt() * 10 + slot
    }
}
