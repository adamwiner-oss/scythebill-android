package com.scythebill.birdlist.android.ui.query

import java.time.LocalDate
import java.time.Year

/**
 * v1 query field UI-state, one per [com.scythebill.birdlist.android.cache.CacheDao.queryResults]
 * WHERE-clause fragment. Mirrors the desktop `LocationQueryField`/`DateQueryField`/
 * `PhotographedQueryField` semantics, collapsed to the modes that make sense against
 * a full-precision Android date picker and the location_ancestors closure table.
 */
data class LocationFieldState(
    val enabled: Boolean = false,
    val locationId: String? = null,
    val mode: LocationMode = LocationMode.IN,
) {
    enum class LocationMode { IN, NOT_IN }

    fun clause(): Pair<String, List<Any>>? {
        if (!enabled || locationId == null) return null
        val sql = when (mode) {
            LocationMode.IN ->
                "s.locationId IN (SELECT locationId FROM location_ancestors WHERE ancestorId = ?)"
            LocationMode.NOT_IN ->
                "s.locationId NOT IN (SELECT locationId FROM location_ancestors WHERE ancestorId = ?)"
        }
        return sql to listOf(locationId)
    }
}

data class DateFieldState(
    val enabled: Boolean = false,
    val mode: DateMode = DateMode.BETWEEN,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    enum class DateMode { ON, BETWEEN, AFTER, BEFORE, THIS_YEAR }

    fun clause(): Pair<String, List<Any>>? {
        if (!enabled) return null
        return when (mode) {
            DateMode.ON -> from?.let { "s.epochDay = ?" to listOf(it.toEpochDay()) }
            DateMode.BETWEEN -> {
                if (from == null || to == null) null
                else "s.epochDay BETWEEN ? AND ?" to listOf(from.toEpochDay(), to.toEpochDay())
            }
            DateMode.AFTER -> from?.let { "s.epochDay > ?" to listOf(it.toEpochDay()) }
            DateMode.BEFORE -> to?.let { "s.epochDay < ?" to listOf(it.toEpochDay()) }
            DateMode.THIS_YEAR -> {
                val year = Year.now()
                "s.epochDay BETWEEN ? AND ?" to listOf(
                    year.atDay(1).toEpochDay(),
                    year.atMonthDay(java.time.MonthDay.of(12, 31)).toEpochDay(),
                )
            }
        }
    }
}

data class PhotographedFieldState(
    val enabled: Boolean = false,
    val hasPhoto: Boolean = true,
) {
    fun clause(): Pair<String, List<Any>>? {
        if (!enabled) return null
        return "s.photographed = ?" to listOf(if (hasPhoto) 1 else 0)
    }
}
