package com.office.tracker.util

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val BBCLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT)

/** "HH:mm" string from epoch millis (display-agnostic, stable). */
fun formatHHmm(millis: Long): String =
    LocalTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(CLOCK)

/** 12-hour display like "10:18 am" from epoch millis. */
fun format12hMillis(millis: Long): String =
    LocalTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(BBCLOCK).lowercase(Locale.ROOT)

/**
 * Normalizes any stored time string into a consistent 12-hour display form
 * like "10:18 am" / "7:48 pm", regardless of whether it was logged by the app
 * (24h "HH:mm") or imported from the seed ("h:mm am/pm").
 */
fun format12h(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    val trimmed = raw.trim()
    val m = Regex(
        "(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(am|pm)?",
        RegexOption.IGNORE_CASE
    ).find(trimmed) ?: return null

    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    var meridian = m.groupValues[3].takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)

    if (meridian == null) {
        meridian = if (h in 0..11) "am" else "pm"
        h %= 12
        if (h == 0) h = 12
    } else {
        h %= 12
        if (h == 0) h = 12
    }

    return "%d:%02d %s".format(Locale.ROOT, h, min, meridian)
}

/** Engineering helper used by integration/backfill code. */
fun localDateTimeFromMillis(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
