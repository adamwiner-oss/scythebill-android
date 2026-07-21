package com.scythebill.birdlist.android.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(rows: List<LocationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationAncestors(rows: List<LocationAncestorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(rows: List<TripEntity>)

    @Insert
    suspend fun insertSightings(rows: List<SightingEntity>): List<Long>

    @Insert
    suspend fun insertSightingDetails(rows: List<SightingDetailsEntity>)

    @Insert
    suspend fun insertSightingTaxa(rows: List<SightingTaxonEntity>)

    @Query("SELECT * FROM cache_metadata WHERE id = 0")
    suspend fun getMetadata(): CacheMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: CacheMetadataEntity)

    @Query("DELETE FROM sighting_taxa")
    suspend fun clearSightingTaxa()

    @Query("SELECT DISTINCT taxonId FROM sighting_taxa")
    suspend fun getSightedTaxonIds(): List<String>

    @Query("DELETE FROM sighting_details")
    suspend fun clearSightingDetails()

    @Query("DELETE FROM sightings")
    suspend fun clearSightings()

    @Query("DELETE FROM location_ancestors")
    suspend fun clearLocationAncestors()

    @Query("DELETE FROM locations")
    suspend fun clearLocations()

    @Query("DELETE FROM trips")
    suspend fun clearTrips()

    @Transaction
    suspend fun clearAll() {
        clearSightingTaxa()
        clearSightingDetails()
        clearSightings()
        clearLocationAncestors()
        clearLocations()
        clearTrips()
    }

    @Query(
        """
        SELECT s.* FROM sightings s
        JOIN location_ancestors la ON la.locationId = s.locationId
        WHERE la.ancestorId = :locationId
        ORDER BY s.epochDay
        """
    )
    suspend fun sightingsUnderLocation(locationId: String): List<SightingEntity>

    @Query("SELECT * FROM locations")
    suspend fun getAllLocations(): List<LocationEntity>

    @RawQuery
    suspend fun queryResults(query: SupportSQLiteQuery): List<QueryResultRow>
}
