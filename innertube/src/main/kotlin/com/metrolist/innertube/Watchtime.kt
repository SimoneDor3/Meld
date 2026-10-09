package com.metrolist.innertube

import io.ktor.http.URLBuilder
import java.util.Locale

/** Player state carried by a watchtime ping. */
enum class WatchtimeState(
    internal val value: String,
) {
    PLAYING("playing"),
    PAUSED("paused"),
}

/**
 * Completes a `playbackTracking.videostatsWatchtimeUrl` the way the web player does: the base URL
 * already identifies the video and player session (`docid`, `ei`, `plid`, `vm`, ...), the ping adds
 * the media range played since the previous ping. YouTube resumes the video at the last [positionSec].
 */
internal fun buildWatchtimeUrl(
    baseUrl: String,
    cpn: String,
    segmentStartSec: Double,
    segmentEndSec: Double,
    positionSec: Double,
    lengthSec: Double,
    elapsedRealSec: Double,
    state: WatchtimeState,
    final: Boolean,
): String =
    URLBuilder(baseUrl)
        .apply {
            parameters["ver"] = "2"
            parameters["c"] = "WEB_REMIX"
            parameters["cpn"] = cpn
            parameters["cmt"] = positionSec.toStatsTime()
            parameters["st"] = segmentStartSec.toStatsTime()
            parameters["et"] = segmentEndSec.toStatsTime()
            if (lengthSec > 0) parameters["len"] = lengthSec.toStatsTime()
            parameters["rt"] = elapsedRealSec.toStatsTime()
            // Milliseconds since the last user input, unknown to a background player: report an attended session.
            parameters["lact"] = "1"
            parameters["state"] = state.value
            parameters["volume"] = "100"
            parameters["muted"] = "0"
            if (final) parameters["final"] = "1"
        }.buildString()

private fun Double.toStatsTime() = "%.3f".format(Locale.ROOT, this)
