package com.scythebill.birdlist.android.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DatePrecision { YEAR, MONTH, DAY }

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val displayName: String,
    val type: String?,
    val parentId: String?,
    val latitude: Double?,
    val longitude: Double?,
)

@Entity(
    tableName = "location_ancestors",
    primaryKeys = ["locationId", "ancestorId"],
    indices = [Index("ancestorId")],
)
data class LocationAncestorEntity(
    val locationId: String,
    val ancestorId: String,
    val depth: Int,
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val locationId: String?,
    val startEpochDay: Long,
    val endEpochDay: Long,
)

@Entity(
    tableName = "sightings",
    indices = [Index("locationId"), Index("epochDay"), Index("tripId"), Index("photographed")],
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: String,
    val epochDay: Long?,
    val datePrecision: DatePrecision?,
    val tripId: String?,
    val sightingStatus: String?,
    val breedingCode: String?,
    val male: Boolean,
    val female: Boolean,
    val immature: Boolean,
    val adult: Boolean,
    val photographed: Boolean,
    val heardOnly: Boolean,
    val approximateNumber: String?,
    val firstForTaxon: Boolean,
)

@Entity(tableName = "sighting_details")
data class SightingDetailsEntity(
    @PrimaryKey val sightingId: Long,
    val description: String?,
    val photoUrisJson: String?,
)

@Entity(
    tableName = "sighting_taxa",
    primaryKeys = ["sightingId", "taxonId"],
    indices = [Index("taxonId")],
)
data class SightingTaxonEntity(val sightingId: Long, val taxonId: String)

@Entity(tableName = "cache_metadata")
data class CacheMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val sourceUri: String,
    val sourceSize: Long,
    val sourceLastModified: Long,
    val taxonomyVersion: String,
    val builtAtEpochMillis: Long,
)

/** Row shape produced by [CacheDao.queryResults]'s dynamically-built SQL. */
data class QueryResultRow(
    val sightingId: Long,
    val locationId: String,
    val epochDay: Long?,
    val datePrecision: DatePrecision?,
    val photographed: Boolean,
    val heardOnly: Boolean,
    val sightingStatus: String?,
    val photoUrisJson: String?,
    val taxonId: String,
)
