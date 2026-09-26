package ca.saboor.larpdot.visualizer

import org.junit.Assert.assertEquals
import org.junit.Test

class BpmDetectorTest {

    @Test
    fun cleanTitle_removesParentheticalFeaturesAndVersions() {
        assertEquals("Starboy", BpmDetector.cleanTitle("Starboy (feat. Daft Punk)"))
        assertEquals("bad guy", BpmDetector.cleanTitle("bad guy (with Justin Bieber)"))
        assertEquals("Cruel Summer", BpmDetector.cleanTitle("Cruel Summer - Remastered 2024"))
        assertEquals("Not Like Us", BpmDetector.cleanTitle("Not Like Us [Official Audio]"))
        assertEquals("Espresso", BpmDetector.cleanTitle("Espresso - Single Version"))
        assertEquals("Levitating", BpmDetector.cleanTitle("Levitating (feat. DaBaby) - Bonus Track"))
        assertEquals("Good Luck, Babe!", BpmDetector.cleanTitle("Good Luck, Babe!"))
    }

    @Test
    fun cleanArtist_extractsPrimaryArtist() {
        assertEquals("The Weeknd", BpmDetector.cleanArtist("The Weeknd, Daft Punk"))
        assertEquals("Dua Lipa", BpmDetector.cleanArtist("Dua Lipa & DaBaby"))
        assertEquals("Lady Gaga", BpmDetector.cleanArtist("Lady Gaga, Bruno Mars"))
        assertEquals("Kendrick Lamar", BpmDetector.cleanArtist("Kendrick Lamar feat. Future"))
        assertEquals("Sabrina Carpenter", BpmDetector.cleanArtist("Sabrina Carpenter"))
    }

    @Test
    fun defaultBpm_isStandard120() {
        assertEquals(120f, BpmDetector.DEFAULT_BPM)
    }
}
