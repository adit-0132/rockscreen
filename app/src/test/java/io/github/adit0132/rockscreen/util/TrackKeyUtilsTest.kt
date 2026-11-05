package io.github.adit0132.rockscreen.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TrackKeyUtilsTest {

    @Test
    fun latinKeysKeepTheirOldShape() {
        assertEquals("the strokes::the adults are talking", TrackKeyUtils.make("The Strokes", "The Adults Are Talking"))
        assertEquals("guns n roses::dont cry", TrackKeyUtils.make("Guns N' Roses", "Don't Cry"))
        assertEquals("a b::c", TrackKeyUtils.make("  A   B ", "C!"))
    }

    @Test
    fun nonLatinTitlesNoLongerCollapseToEmpty() {
        assertEquals("米津玄師::感電", TrackKeyUtils.make("米津玄師", "感電"))
        assertEquals("кино::группа крови", TrackKeyUtils.make("Кино", "Группа крови"))
        assertEquals("beyoncé::halo", TrackKeyUtils.make("Beyoncé", "Halo"))
        assertEquals("sigur rós::hoppípolla", TrackKeyUtils.make("Sigur Rós", "Hoppípolla"))
    }

    @Test
    fun differentNonLatinSongsGetDifferentKeys() {
        // Previously all three were "::" or "arijit singh::" and shared one cache entry.
        assertNotEquals(TrackKeyUtils.make("Кино", "Группа крови"), TrackKeyUtils.make("ДДТ", "Осень"))
        assertNotEquals(TrackKeyUtils.make("Arijit Singh", "तुम ही हो"), TrackKeyUtils.make("Arijit Singh", "चन्ना मेरेया"))
        assertNotEquals(TrackKeyUtils.make("BTS", "봄날"), TrackKeyUtils.make("BTS", "Butter"))
    }

    @Test
    fun devanagariVowelSignsAreKept() {
        // Combining marks (matras) are part of the word; dropping them would merge different titles.
        assertEquals("arijit singh::तुम ही हो", TrackKeyUtils.make("Arijit Singh", "तुम ही हो"))
    }

    @Test
    fun compatibilityFormsNormalize() {
        // Full-width Latin (common in Japanese metadata) matches its ASCII form.
        assertEquals(TrackKeyUtils.make("YOASOBI", "Idol"), TrackKeyUtils.make("ＹＯＡＳＯＢＩ", "Ｉｄｏｌ"))
    }

    @Test
    fun spotifyRecommendationTagIsDropped() {
        // Exact artist strings Spotify sent on a real device (Spotify 9.1.94, Android 15).
        assertEquals("Kid Cudi", TrackKeyUtils.cleanArtist("Kid Cudi • Recommended for you"))
        assertEquals("Cassius Henry, Kanye West, Freeway, Sequel",
            TrackKeyUtils.cleanArtist("Cassius Henry, Kanye West, Freeway, Sequel • Recommended for you"))
        assertEquals("Kanye West, Mos Def", TrackKeyUtils.cleanArtist("Kanye West, Mos Def"))
        assertEquals("", TrackKeyUtils.cleanArtist(""))
    }
}
