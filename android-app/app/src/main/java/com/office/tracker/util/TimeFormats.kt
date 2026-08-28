package com.office.tracker.util

import java.util.Locale

/**
 * Normalizes any stored time string into a consistent 12-hour display form
 * like "10:18 am" / "7:48 pm", regardless of whether it was logged by the app
 * (24h "HH:mm"), imported from the seed (already "h:mm am/pm"), or otherwise.
 *
 * Supported inputs:
 *   - "14:40"            -> "2:40 pm"
 *   - "14:40:22"         -> "2:40 pm"
 *   - "10:18 am" / "pm"  -> unchanged-ish (normalized spacing/lowercase)
 *   - "7:48" (12h)       -> treated as 24h -> "7:48 am"
 *   - null / "" / garbage -> returns null (caller falls back to "—")
 */
fun format12h(raw: String?): String? {
    if (raw.isNullOrBlank()) return null

    val trimmed = raw.trim()
    // Match "H(H):MM(:SS)?( am| pm)?" — 12- or 24-hour, with optional seconds & meridian.
    val m = Regex(
        "(\\d{1,2}):(\\d{2})(?::\\d{2})?\\s*(am|pm)?",
        RegexOption.IGNORE_CASE
    ).find(trimmed) ?: return null

    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    var meridian = m.groupValues[3].takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)

    if (meridian == null) {
        // No explicit meridian -> input is 24h. Convert.
        meridian = if (h in 0..11) "am" else "pm"
        h %= 12
        if (h == 0) h = 12
    } else {
        // Explicit meridian -> input is 12h; just ensure valid 1..12.
        h %= 12
        if (h == 0) h = 12
    }

    return "%d:%02d %s".format(Locale.ROOT, h, min, meridian)
}
