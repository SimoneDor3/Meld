package com.metrolist.music.models

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.spotify.models.SpotifyHomeFeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyHomeBuildTest {

    private fun episode(id: String, durationSec: Int = 3600) = SongItem(
        id = id,
        title = "Episode $id",
        artists = listOf(Artist(name = "Show", id = null)),
        duration = durationSec,
        thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        isEpisode = true,
    )

    private fun played(id: String, positionMs: Long, durationSec: Int = 3600) =
        SongEntity(id = id, title = "Episode $id", duration = durationSec, playbackPosition = positionMs)

    private fun playlist(n: Int) = SpotifyHomeFeedItem.Playlist(
        uri = "spotify:playlist:$n", id = "$n", name = "Playlist $n", description = null, format = null,
        totalCount = 0, imageUrl = null, extractedColorHex = null, ownerName = null, madeForUsername = null,
    )

    private val show = PodcastItem(
        id = "MPSPshow", title = "Show", author = null, episodeCountText = null,
        thumbnail = null, playEndpoint = null, shuffleEndpoint = null,
    )

    private val spotifyGrid = SpotifyHomeSection(
        title = "", type = SectionType.SHORTCUTS, shortcuts = (1..6).map(::playlist),
    )
    private val spotifyMusic = (1..6).map { SpotifyHomeSection(title = "Music $it", type = SectionType.PLAYLISTS) }

    @Test
    fun `episode state - unplayed is new, partial shows progress, near the end is completed`() {
        val new = episode("a").toHomeEpisode(local = null)
        assertTrue(new.isNew)
        assertNull(new.progress)

        val partial = episode("b").toHomeEpisode(played("b", positionMs = 900_000))
        assertFalse(partial.isNew)
        assertEquals(0.25f, partial.progress!!, 0.001f)

        val done = episode("c").toHomeEpisode(played("c", positionMs = 3_580_000))
        assertFalse(done.isNew)
        assertNull(done.progress)
    }

    @Test
    fun `unknown local duration falls back to the YouTube duration`() {
        val partial = episode("a", durationSec = 1000).toHomeEpisode(played("a", positionMs = 500_000, durationSec = -1))
        assertEquals(0.5f, partial.progress!!, 0.001f)
    }

    @Test
    fun `all view puts up to four unfinished episodes before music, eight tiles total`() {
        val episodes = listOf(episode("new1"), episode("done"), episode("new2"), episode("half"))
        val local = mapOf(
            "done" to played("done", positionMs = 3_590_000),
            "half" to played("half", positionMs = 1_800_000),
        )

        val home = buildSpotifyHome(listOf(spotifyGrid) + spotifyMusic, episodes, listOf(show), local, SpotifyHomeFilter.ALL)

        val grid = home.first()
        assertEquals(SectionType.SHORTCUTS, grid.type)
        // In progress first, then unplayed in feed order; the finished one is left out.
        assertEquals(listOf("half", "new1", "new2"), grid.episodes.map { it.song.id })
        assertEquals(5, grid.shortcuts.size)
        assertEquals(SectionType.SHOWS, home[5].type)
        assertEquals(1 + spotifyMusic.size + 1, home.size)
    }

    @Test
    fun `music view is Spotify only, podcasts view is YouTube only`() {
        val episodes = listOf(episode("a"))
        val sections = listOf(spotifyGrid) + spotifyMusic

        assertEquals(sections, buildSpotifyHome(sections, episodes, listOf(show), emptyMap(), SpotifyHomeFilter.MUSIC))

        val podcasts = buildSpotifyHome(sections, episodes, listOf(show), emptyMap(), SpotifyHomeFilter.PODCASTS)
        assertEquals(listOf(SectionType.SHORTCUTS, SectionType.SHOWS), podcasts.map { it.type })
        assertTrue(podcasts.first().shortcuts.isEmpty())
    }

    @Test
    fun `without YouTube podcasts the all view equals today's Spotify home`() {
        val sections = listOf(spotifyGrid) + spotifyMusic
        val home = buildSpotifyHome(sections, emptyList(), emptyList(), emptyMap(), SpotifyHomeFilter.ALL)
        assertEquals(6, home.first().shortcuts.size)
        assertEquals(sections.drop(1), home.drop(1))
    }
}
