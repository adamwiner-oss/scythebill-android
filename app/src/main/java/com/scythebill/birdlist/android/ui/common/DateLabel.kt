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

/** Formats a start/end epoch-day range (e.g. a trip's span) for display. */
fun formatDateRange(startEpochDay: Long?, endEpochDay: Long?): String {
    if (startEpochDay == null || endEpochDay == null) return "Unknown date"
    val start = LocalDate.ofEpochDay(startEpochDay)
    val end = LocalDate.ofEpochDay(endEpochDay)
    if (start == end) {
        return start.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
    return when {
        start.year != end.year ->
            "${start.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} – " +
                end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        start.month != end.month ->
            "${start.format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        else ->
            "${start.format(DateTimeFormatter.ofPattern("MMM d"))}–${end.day}, ${end.year}"
    }
}

private val LocalDate.day: Int get() = dayOfMonth
