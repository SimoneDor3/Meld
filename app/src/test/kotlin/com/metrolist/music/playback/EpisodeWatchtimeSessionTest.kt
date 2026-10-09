package com.metrolist.music.playback

import com.metrolist.innertube.WatchtimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeWatchtimeSessionTest {

    private fun session(startPositionMs: Long = 0L) =
        EpisodeWatchtimeSession(
            mediaId = "episode",
            cpn = "cpn",
            playbackUrl = "https://example.com/playback",
            watchtimeUrl = "https://example.com/watchtime",
            startRealtimeMs = 1_000L,
            startPositionMs = startPositionMs,
        )

    @Test
    fun `first playing ping waits for the first delay then pings are spaced by the interval`() {
        val s = session()
        assertNull(s.playingPingIfDue(positionMs = 5_000, lengthMs = 600_000, nowRealtimeMs = 6_000))

        val first = s.playingPingIfDue(positionMs = 15_000, lengthMs = 600_000, nowRealtimeMs = 16_000)
        assertNotNull(first)
        assertEquals(0.0, first!!.segmentStartSec, 0.0)
        assertEquals(15.0, first.segmentEndSec, 0.0)
        assertEquals(15.0, first.positionSec, 0.0)
        assertEquals(600.0, first.lengthSec, 0.0)
        assertEquals(15.0, first.elapsedRealSec, 0.0)
        assertEquals(WatchtimeState.PLAYING, first.state)
        assertFalse(first.final)

        // 15 s tick after the first ping: not due yet.
        assertNull(s.playingPingIfDue(positionMs = 30_000, lengthMs = 600_000, nowRealtimeMs = 31_000))

        val second = s.playingPingIfDue(positionMs = 45_000, lengthMs = 600_000, nowRealtimeMs = 46_000)
        assertNotNull(second)
        // The new range starts where the previous one ended.
        assertEquals(15.0, second!!.segmentStartSec, 0.0)
        assertEquals(45.0, second.segmentEndSec, 0.0)
    }

    @Test
    fun `seek closes the played range and restarts it at the new position`() {
        val s = session()
        val ping = s.seekPing(oldPositionMs = 20_000, newPositionMs = 300_000, lengthMs = 600_000, nowRealtimeMs = 21_000, playing = true)
        assertNotNull(ping)
        assertEquals(0.0, ping!!.segmentStartSec, 0.0)
        assertEquals(20.0, ping.segmentEndSec, 0.0)
        assertEquals(300.0, ping.positionSec, 0.0)
        assertEquals(WatchtimeState.PLAYING, ping.state)

        val paused = s.pausedPing(positionMs = 310_000, lengthMs = 600_000, nowRealtimeMs = 31_000)
        assertEquals(300.0, paused.segmentStartSec, 0.0)
        assertEquals(310.0, paused.segmentEndSec, 0.0)
        assertEquals(WatchtimeState.PAUSED, paused.state)
    }

    @Test
    fun `seek right after the previous one only moves the range start`() {
        val s = session(startPositionMs = 100_000)
        assertNull(s.seekPing(oldPositionMs = 100_500, newPositionMs = 110_000, lengthMs = 600_000, nowRealtimeMs = 2_000, playing = true))
        assertEquals(110_000L, s.lastPositionMs)

        val final = s.finalPing(positionMs = 130_000, lengthMs = 600_000, nowRealtimeMs = 22_000)
        assertEquals(110.0, final.segmentStartSec, 0.0)
        assertEquals(130.0, final.segmentEndSec, 0.0)
    }

    @Test
    fun `final ping of a finished episode reports the full length`() {
        val s = session()
        s.playingPingIfDue(positionMs = 15_000, lengthMs = 600_000, nowRealtimeMs = 16_000)

        // Length unknown at the end (player already moved on): the earlier length is reused.
        val final = s.finalPing(positionMs = 590_000, lengthMs = 0, nowRealtimeMs = 600_000, ended = true)
        assertEquals(600.0, final.positionSec, 0.0)
        assertEquals(600.0, final.segmentEndSec, 0.0)
        assertEquals(600.0, final.lengthSec, 0.0)
        assertEquals(WatchtimeState.PAUSED, final.state)
        assertTrue(final.final)
    }

    @Test
    fun `range never runs backwards`() {
        val s = session(startPositionMs = 50_000)
        val ping = s.pausedPing(positionMs = 10_000, lengthMs = 0, nowRealtimeMs = 2_000)
        assertEquals(50.0, ping.segmentStartSec, 0.0)
        assertEquals(50.0, ping.segmentEndSec, 0.0)
        assertEquals(10.0, ping.positionSec, 0.0)
        assertEquals(0.0, ping.lengthSec, 0.0)
    }
}
