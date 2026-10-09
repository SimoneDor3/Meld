package com.metrolist.music.playback

import com.metrolist.innertube.WatchtimeState

/** One `videostatsWatchtimeUrl` ping, in the units [com.metrolist.innertube.YouTube.reportWatchtime] takes. */
internal data class WatchtimePing(
    val segmentStartSec: Double,
    val segmentEndSec: Double,
    val positionSec: Double,
    val lengthSec: Double,
    val elapsedRealSec: Double,
    val state: WatchtimeState,
    val final: Boolean = false,
)

/**
 * Watchtime state of one YouTube playback of a podcast episode, so YouTube resumes it where it was
 * left in Meld. Pure bookkeeping: the caller registers the playback with [cpn] and sends the pings.
 *
 * The web player pings ~10, 20, 30 s after start and then every 40 s. Meld only checks on its
 * existing 15 s service tick, so the first ping goes out on the first tick at least
 * [FIRST_PING_DELAY_MS] after start and later ones at least [PING_INTERVAL_MS] apart (every other
 * tick), which keeps the resume point at most ~30 s behind without waking the device more often.
 *
 * Each ping reports the media range played since the previous one (st..et) and chains it: the next
 * range starts where this one ended, or at the new position after a seek.
 */
internal class EpisodeWatchtimeSession(
    val mediaId: String,
    val cpn: String,
    val playbackUrl: String,
    val watchtimeUrl: String,
    private val startRealtimeMs: Long,
    startPositionMs: Long,
) {
    private var segmentStartMs = startPositionMs
    private var nextPingDueRealtimeMs = startRealtimeMs + FIRST_PING_DELAY_MS
    private var knownLengthMs = 0L

    /** Last position seen by this session, for closing it once the player has moved to another item. */
    var lastPositionMs = startPositionMs
        private set

    fun playingPingIfDue(
        positionMs: Long,
        lengthMs: Long,
        nowRealtimeMs: Long,
    ): WatchtimePing? {
        lastPositionMs = positionMs
        if (nowRealtimeMs < nextPingDueRealtimeMs) return null
        return ping(positionMs, positionMs, lengthMs, nowRealtimeMs, WatchtimeState.PLAYING)
    }

    fun pausedPing(
        positionMs: Long,
        lengthMs: Long,
        nowRealtimeMs: Long,
    ): WatchtimePing = ping(positionMs, positionMs, lengthMs, nowRealtimeMs, WatchtimeState.PAUSED)

    /**
     * Closes the range played up to [oldPositionMs] and restarts it at [newPositionMs]. Returns null
     * (only moving the range start) when almost nothing was played since the last ping, so repeated
     * skip-forward taps don't send a ping each.
     */
    fun seekPing(
        oldPositionMs: Long,
        newPositionMs: Long,
        lengthMs: Long,
        nowRealtimeMs: Long,
        playing: Boolean,
    ): WatchtimePing? {
        val played = oldPositionMs - segmentStartMs
        if (played in 0 until MIN_SEEK_SEGMENT_MS) {
            segmentStartMs = newPositionMs
            lastPositionMs = newPositionMs
            return null
        }
        val state = if (playing) WatchtimeState.PLAYING else WatchtimeState.PAUSED
        return ping(oldPositionMs, newPositionMs, lengthMs, nowRealtimeMs, state)
    }

    /**
     * Last ping of the session; [ended] reports the full length so YouTube marks the episode
     * watched. A [lengthMs] of 0 (unknown) falls back to the length seen by earlier calls.
     */
    fun finalPing(
        positionMs: Long,
        lengthMs: Long,
        nowRealtimeMs: Long,
        ended: Boolean = false,
    ): WatchtimePing {
        if (lengthMs > 0) knownLengthMs = lengthMs
        val endMs = if (ended && knownLengthMs > 0) knownLengthMs else positionMs
        return ping(endMs, endMs, knownLengthMs, nowRealtimeMs, WatchtimeState.PAUSED, final = true)
    }

    private fun ping(
        segmentEndMs: Long,
        positionMs: Long,
        lengthMs: Long,
        nowRealtimeMs: Long,
        state: WatchtimeState,
        final: Boolean = false,
    ): WatchtimePing {
        if (lengthMs > 0) knownLengthMs = lengthMs
        val ping =
            WatchtimePing(
                segmentStartSec = segmentStartMs.toSec(),
                // A range never runs backwards, e.g. when the position was restored after the start.
                segmentEndSec = maxOf(segmentStartMs, segmentEndMs).toSec(),
                positionSec = positionMs.toSec(),
                lengthSec = knownLengthMs.toSec(),
                elapsedRealSec = (nowRealtimeMs - startRealtimeMs).coerceAtLeast(0L).toSec(),
                state = state,
                final = final,
            )
        segmentStartMs = positionMs
        lastPositionMs = positionMs
        nextPingDueRealtimeMs = nowRealtimeMs + PING_INTERVAL_MS
        return ping
    }

    private fun Long.toSec() = this / 1000.0

    companion object {
        const val FIRST_PING_DELAY_MS = 10_000L
        const val PING_INTERVAL_MS = 30_000L
        const val MIN_SEEK_SEGMENT_MS = 1_000L
    }
}
