package com.scythebill.birdlist.android.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppContainerTest {

    @Test
    fun taxonomyMappings_constructsWithoutThrowing() {
        val mappings = AppContainer().taxonomyMappings()
        assertThat(mappings).isNotNull()
    }

    @Test
    fun sightingUpgradesPackage_isExcludedFromClasspath() {
        // `sighting/upgrades/**` is excluded from :model's Java sourceSet
        // (see model/build.gradle.kts) so Android never carries the
        // upgrader chain. This test documents that intent and would fail
        // loudly if the exclusion were ever accidentally removed.
        val upgraderClass = "com.scythebill.birdlist.model.sighting.upgrades.UpgraderModule"
        val found = try {
            Class.forName(upgraderClass)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        assertThat(found).isFalse()
    }
}
