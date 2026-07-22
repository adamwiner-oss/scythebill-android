package com.scythebill.birdlist.android.ui.search

import com.scythebill.birdlist.android.cache.LocationEntity
import com.scythebill.birdlist.android.cache.SyntheticLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationIndexerTest {

    private fun location(id: String, displayName: String) = LocationEntity(
        id = id,
        name = displayName,
        displayName = displayName,
        type = null,
        parentId = null,
        latitude = null,
        longitude = null,
    )

    @Test
    fun `indexes real locations by display name`() {
        val index = buildLocationIndexer(listOf(location("us-il-springfield", "Springfield")))
        assertEquals(listOf("us-il-springfield"), index.find("Springfield").toList())
    }

    @Test
    fun `indexes synthetic locations by display name`() {
        val index = buildLocationIndexer(emptyList())
        addSyntheticLocationsToIndex(
            index,
            listOf(SyntheticLocationEntity(id = "***abareg***", displayName = "ABA Region")),
        )
        assertEquals(listOf("***abareg***"), index.find("ABA Region").toList())
    }

    @Test
    fun `real and synthetic locations share one index`() {
        val index = buildLocationIndexer(listOf(location("us", "United States")))
        addSyntheticLocationsToIndex(
            index,
            listOf(SyntheticLocationEntity(id = "***usdepterr***", displayName = "United States (with territories)")),
        )
        assertEquals(
            setOf("us", "***usdepterr***"),
            index.find("United States").toSet(),
        )
    }
}
