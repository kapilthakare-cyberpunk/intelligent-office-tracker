package com.office.tracker.service

/**
 * The two tracking windows. The wire value ("arrival"/"departure") is stored in
 * alarm extras and notifications so old persisted alarms keep working.
 */
enum class WindowType(val wire: String) {
    ARRIVAL("arrival"),
    DEPARTURE("departure");

    companion object {
        fun fromWire(value: String?): WindowType? =
            entries.firstOrNull { it.wire == value }

        val EXTRA_WINDOW_TYPE: String = "window_type"
    }
}
