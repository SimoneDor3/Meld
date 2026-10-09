package com.metrolist.innertube.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicMultiRowListItemRenderer(
    val title: Runs?,
    val subtitle: Runs?,
    @SerialName("secondSubtitle")
    val secondSubtitle: Runs? = null,
    @SerialName("secondarySubtitle")
    val secondarySubtitle: Runs? = null,
    val thumbnail: ThumbnailRenderer?,
    val onTap: NavigationEndpoint?,
    val playbackProgress: PlaybackProgress?,
    val displayStyle: String?,
    val menu: Menu?,
) {
    @Serializable
    data class PlaybackProgress(
        val value: Float? = null,
        val musicPlaybackProgressRenderer: MusicPlaybackProgressRenderer? = null,
    ) {
        @Serializable
        data class MusicPlaybackProgressRenderer(
            val playbackProgressPercentage: Float? = null,
        )

        /** Listened fraction in 0..1, or null when YouTube reports no progress. */
        val fraction: Float?
            get() {
                val percentage = musicPlaybackProgressRenderer?.playbackProgressPercentage
                // The renderer reports 0..100; the bare value's scale is unconfirmed, so accept both.
                val normalized = when {
                    percentage != null -> percentage / 100f
                    value != null -> if (value > 1f) value / 100f else value
                    else -> return null
                }
                return normalized.coerceIn(0f, 1f).takeIf { it > 0f }
            }
    }
}
