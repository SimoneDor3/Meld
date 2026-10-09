/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.models

import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.spotify.models.SpotifyAlbum
import com.metrolist.spotify.models.SpotifyArtist
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import com.metrolist.spotify.models.SpotifyPlaylist
import com.metrolist.spotify.models.SpotifyTrack

/**
 * Represents a section in the Spotify-powered home screen.
 * Each section has a title and contains one type of content, except the untitled
 * [SectionType.SHORTCUTS] grid, which mixes Spotify music [shortcuts] with
 * YouTube Music podcast [episodes].
 */
data class SpotifyHomeSection(
    val title: String,
    val type: SectionType,
    val tracks: List<SpotifyTrack> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val shortcuts: List<SpotifyHomeFeedItem> = emptyList(),
    val episodes: List<HomeEpisode> = emptyList(),
    val shows: List<PodcastItem> = emptyList(),
)

enum class SectionType {
    TRACKS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    SHORTCUTS,
    SHOWS,
}

/** A YouTube Music podcast episode with the listening state shown on its home tile. */
data class HomeEpisode(
    val song: SongItem,
    val progress: Float?,
    val isNew: Boolean,
)

enum class SpotifyHomeFilter {
    ALL,
    MUSIC,
    PODCASTS,
}

private const val ALL_GRID_SIZE = 8
private const val ALL_GRID_MAX_EPISODES = 4

// Roughly where Spotify places "Your shows": after the first few music sections.
private const val ALL_SHOWS_POSITION = 4

/**
 * Builds the visible Spotify home. Spotify provides the music [spotifySections];
 * podcasts come from YouTube Music: [newEpisodes] of followed [shows]. [localPlayback]
 * (video ID → song row) holds Meld's saved positions, which drive the dot and bar.
 */
fun buildSpotifyHome(
    spotifySections: List<SpotifyHomeSection>,
    newEpisodes: List<SongItem>,
    shows: List<PodcastItem>,
    localPlayback: Map<String, SongEntity>,
    filter: SpotifyHomeFilter,
): List<SpotifyHomeSection> {
    // In progress first, then unplayed, then finished; newest first within each (feed order).
    val episodes = newEpisodes
        .distinctBy { it.id }
        .map { it.toHomeEpisode(localPlayback[it.id]) }
        .sortedBy { if (it.progress != null) 0 else if (it.isNew) 1 else 2 }
    val showsSection = shows.takeIf { it.isNotEmpty() }
        ?.let { SpotifyHomeSection(title = "your_shows", type = SectionType.SHOWS, shows = it.distinctBy { show -> show.id }) }

    return when (filter) {
        SpotifyHomeFilter.MUSIC -> spotifySections
        SpotifyHomeFilter.PODCASTS -> listOfNotNull(
            episodes.takeIf { it.isNotEmpty() }
                ?.let { SpotifyHomeSection(title = "", type = SectionType.SHORTCUTS, episodes = it) },
            showsSection,
        )
        SpotifyHomeFilter.ALL -> {
            val gridEpisodes = episodes.filter { it.progress != null || it.isNew }.take(ALL_GRID_MAX_EPISODES)
            val musicShortcuts = spotifySections.firstOrNull { it.type == SectionType.SHORTCUTS }?.shortcuts.orEmpty()
            val grid = SpotifyHomeSection(
                title = "",
                type = SectionType.SHORTCUTS,
                shortcuts = musicShortcuts.take(ALL_GRID_SIZE - gridEpisodes.size),
                episodes = gridEpisodes,
            )
            val rest = spotifySections.filter { it.type != SectionType.SHORTCUTS }.toMutableList()
            showsSection?.let { rest.add(minOf(ALL_SHOWS_POSITION, rest.size), it) }
            listOfNotNull(grid.takeIf { it.shortcuts.isNotEmpty() || it.episodes.isNotEmpty() }) + rest
        }
    }
}

internal fun SongItem.toHomeEpisode(local: SongEntity?): HomeEpisode {
    val position = local?.playbackPosition?.takeIf { it > 0 }
    val totalMs = (local?.duration?.takeIf { it > 0 } ?: duration ?: 0) * 1000L
    // MusicService saves the position every 15 s, so the last save can stop short of the end.
    val completed = position != null && totalMs > 0 && position >= totalMs - 30_000
    return HomeEpisode(
        song = this,
        progress = if (position != null && !completed && totalMs > 0) position.toFloat() / totalMs else null,
        isNew = position == null,
    )
}
