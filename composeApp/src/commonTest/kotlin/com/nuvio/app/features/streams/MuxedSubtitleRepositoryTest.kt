package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MuxedSubtitleRepositoryTest {

    @Test
    fun `two and three letter tags map to the same label`() {
        assertEquals("ESP", muxedSubtitleLanguageLabel("es"))
        assertEquals("ESP", muxedSubtitleLanguageLabel("spa"))
        assertEquals("ENG", muxedSubtitleLanguageLabel("en"))
        assertEquals("ENG", muxedSubtitleLanguageLabel("eng"))
        assertEquals("POR", muxedSubtitleLanguageLabel("pt"))
    }

    @Test
    fun `regional and script subtags keep the primary language`() {
        assertEquals("ESP", muxedSubtitleLanguageLabel("es-419"))
        assertEquals("POR", muxedSubtitleLanguageLabel("pt_BR"))
        assertEquals("CHI", muxedSubtitleLanguageLabel("zh-Hans"))
        assertEquals("ENG", muxedSubtitleLanguageLabel("  EN  "))
    }

    @Test
    fun `missing or undefined languages have no label`() {
        assertNull(muxedSubtitleLanguageLabel(null))
        assertNull(muxedSubtitleLanguageLabel(""))
        assertNull(muxedSubtitleLanguageLabel("und"))
        assertNull(muxedSubtitleLanguageLabel("unknown"))
        assertNull(muxedSubtitleLanguageLabel("mul"))
    }

    @Test
    fun `unknown tags fall back to their own letters`() {
        assertEquals("XX", muxedSubtitleLanguageLabel("xx"))
        assertEquals("KLI", muxedSubtitleLanguageLabel("kli"))
    }

    @Test
    fun `visible labels honor the settings filter`() {
        val tracks = MuxedSubtitleResult.Tracks(listOf("ESP", "ENG", "JPN"))
        assertEquals(listOf("ESP", "ENG", "JPN"), tracks.visibleLabels(emptySet()))
        assertEquals(listOf("ESP"), tracks.visibleLabels(setOf("ESP")))
        assertEquals(emptyList(), tracks.visibleLabels(setOf("FRA")))
        assertEquals(emptyList(), MuxedSubtitleResult.NoTracks.visibleLabels(emptySet()))
        assertEquals(emptyList(), MuxedSubtitleResult.Unavailable().visibleLabels(setOf("ESP")))
    }

    @Test
    fun `unavailable results carry the reason of the failure`() {
        assertEquals(
            MuxedSubtitleFailure.DebridUnavailable,
            MuxedSubtitleResult.Unavailable(MuxedSubtitleFailure.DebridUnavailable).reason,
        )
        assertEquals(MuxedSubtitleFailure.Unreadable, MuxedSubtitleResult.Unavailable().reason)
    }
}
