package com.scythebill.birdlist.android.ui.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryFieldsTest {

    @Test
    fun `disabled field has no clause`() {
        assertNull(LocationFieldState(enabled = false, locationId = "us").clause())
    }

    @Test
    fun `no location selected has no clause`() {
        assertNull(LocationFieldState(enabled = true, locationId = null).clause())
    }

    @Test
    fun `real location id queries the ancestors closure table`() {
        val (sql, args) = LocationFieldState(enabled = true, locationId = "us-il").clause()!!
        assertEquals(
            "s.locationId IN (SELECT locationId FROM location_ancestors WHERE ancestorId = ?)",
            sql,
        )
        assertEquals(listOf("us-il"), args)
    }

    @Test
    fun `real location id in NOT_IN mode excludes via the ancestors closure table`() {
        val (sql, _) = LocationFieldState(
            enabled = true,
            locationId = "us-il",
            mode = LocationFieldState.LocationMode.NOT_IN,
        ).clause()!!
        assertEquals(
            "s.locationId NOT IN (SELECT locationId FROM location_ancestors WHERE ancestorId = ?)",
            sql,
        )
    }

    @Test
    fun `synthetic location id queries the synthetic membership table`() {
        val (sql, args) = LocationFieldState(enabled = true, locationId = "***abareg***").clause()!!
        assertEquals(
            "s.locationId IN (SELECT locationId FROM synthetic_location_members WHERE syntheticId = ?)",
            sql,
        )
        assertEquals(listOf("***abareg***"), args)
    }

    @Test
    fun `synthetic location id in NOT_IN mode excludes via the synthetic membership table`() {
        val (sql, _) = LocationFieldState(
            enabled = true,
            locationId = "***abareg***",
            mode = LocationFieldState.LocationMode.NOT_IN,
        ).clause()!!
        assertEquals(
            "s.locationId NOT IN (SELECT locationId FROM synthetic_location_members WHERE syntheticId = ?)",
            sql,
        )
    }
}
