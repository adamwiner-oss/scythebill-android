package com.scythebill.birdlist.android.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LocationEntity::class,
        LocationAncestorEntity::class,
        TripEntity::class,
        SightingEntity::class,
        SightingDetailsEntity::class,
        SightingTaxonEntity::class,
        CacheMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ScythebillDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        @Volatile
        private var instance: ScythebillDatabase? = null

        fun get(context: Context): ScythebillDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScythebillDatabase::class.java,
                    "scythebill-cache.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { instance = it }
            }
    }
}
