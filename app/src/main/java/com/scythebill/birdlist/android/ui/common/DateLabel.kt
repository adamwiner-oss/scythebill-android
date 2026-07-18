package com.scythebill.birdlist.android.ui.common

import com.scythebill.birdlist.android.cache.DatePrecision
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Formats an epoch-day sighting date at the given precision for display. */
fun formatDate(epochDay: Long?, precision: DatePrecision?): String {
    if (epochDay == null) return "Unknown date"
    val date = LocalDate.ofEpochDay(epochDay)
    return when (precision) {
        DatePrecision.YEAR -> date.year.toString()
        DatePrecision.MONTH ->
            "${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.year}"
        DatePrecision.DAY, null -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}
