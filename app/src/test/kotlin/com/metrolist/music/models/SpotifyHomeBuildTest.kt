package com.metrolist.music.models

import com.metrolist.innertube.models.Album
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

    private fun episode(id: String, durationSec: Int = 3600, show: String = "Show $id", album: Album? = null) = SongItem(
        id = id,
        title = "Episode $id",
        artists = listOf(Artist(name = show, id = null)),
        album = album,
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

    private fun show(id: String, title: String, author: String? = null) = PodcastItem(
        id = id, title = title, author = author?.let { Artist(name = it, id = null) }, episodeCountText = null,
        thumbnail = "https://show/$id.jpg", playEndpoint = null, shuffleEndpoint = null,
    )

    private val show = show("MPSPshow", "Show")

    private fun podcasts(episodes: List<SongItem>, shows: List<PodcastItem> = emptyList(), local: Map<String, SongEntity> = emptyMap()) =
        buildSpotifyHome(emptyList(), episodes, shows, local, SpotifyHomeFilter.PODCASTS).first().podcasts

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
    fun `podcast state - new if any episode is unplayed, progress from the first started one`() {
        val podcast = HomePodcast(
            id = null, title = "P", thumbnail = null,
            episodes = listOf(
                episode("a").toHomeEpisode(played("a", positionMs = 3_590_000)),
                episode("b").toHomeEpisode(played("b", positionMs = 900_000)),
                episode("c").toHomeEpisode(played("c", positionMs = 1_800_000)),
                episode("d").toHomeEpisode(local = null),
            ),
        )
        assertTrue(podcast.hasNew)
        assertEquals(0.25f, podcast.progress!!, 0.001f)
        assertEquals("a", podcast.latestEpisode.song.id)

        val finished = podcast.copy(episodes = podcast.episodes.take(1))
        assertFalse(finished.hasNew)
        assertNull(finished.progress)
    }

    @Test
    fun `episodes are grouped by show ID in feed order`() {
        val moka = Album(name = "La Moka", id = "MPSPmoka")
        val result = podcasts(
            listOf(
                episode("m2", show = "Author", album = moka),
                episode("x", show = "Other", album = Album(name = "Other", id = "MPSPother")),
                episode("m1", show = "Author", album = moka),
            ),
        )
        assertEquals(listOf("MPSPmoka", "MPSPother"), result.map { it.id })
        assertEquals("La Moka", result.first().title)
        assertEquals(listOf("m2", "m1"), result.first().episodes.map { it.song.id })
        assertEquals("https://i.ytimg.com/vi/m2/hqdefault.jpg", result.first().thumbnail)
    }

    @Test
    fun `episodes without a show ID match a followed show by name`() {
        val moka = show("MPSPmoka", "La Moka", author = "Moka Media")
        val result = podcasts(
            listOf(
                episode("a", show = " la moka "),
                episode("b", show = "MOKA MEDIA"),
                episode("c", show = "Author", album = Album(name = "La Moka", id = "MPSPmoka")),
            ),
            shows = listOf(moka),
        )
        assertEquals(1, result.size)
        assertEquals("MPSPmoka", result.single().id)
        assertEquals("La Moka", result.single().title)
        assertEquals("https://show/MPSPmoka.jpg", result.single().thumbnail)
        assertEquals(listOf("a", "b", "c"), result.single().episodes.map { it.song.id })
    }

    @Test
    fun `unmatched episodes are grouped by show name without an ID`() {
        val result = podcasts(listOf(episode("a", show = "Indie"), episode("b", show = "indie "), episode("c", show = "Else")))
        assertEquals(listOf(null, null), result.map { it.id })
        assertEquals(listOf("Indie", "Else"), result.map { it.title })
        assertEquals(listOf("a", "b"), result.first().episodes.map { it.song.id })
    }

    @Test
    fun `podcasts in progress come first, then those with new episodes, then the rest`() {
        val result = podcasts(
            listOf(episode("done", show = "Done"), episode("new", show = "New"), episode("half", show = "Half")),
            local = mapOf(
                "done" to played("done", positionMs = 3_590_000),
                "half" to played("half", positionMs = 1_800_000),
            ),
        )
        assertEquals(listOf("Half", "New", "Done"), result.map { it.title })
    }

    @Test
    fun `all view puts up to four unfinished podcasts before music, eight tiles total`() {
        val episodes = listOf(
            episode("new1"), episode("done"), episode("new2"), episode("half"),
            episode("new3"), episode("new4"), episode("half-older", show = "Show half"),
        )
        val local = mapOf(
            "done" to played("done", positionMs = 3_590_000),
            "half" to played("half", positionMs = 1_800_000),
            "half-older" to played("half-older", positionMs = 900_000),
        )

        val home = buildSpotifyHome(listOf(spotifyGrid) + spotifyMusic, episodes, listOf(show), local, SpotifyHomeFilter.ALL)

        val grid = home.first()
        assertEquals(SectionType.SHORTCUTS, grid.type)
        // In progress first, then new in feed order, capped at four; the finished one is left out.
        assertEquals(listOf("Show half", "Show new1", "Show new2", "Show new3"), grid.podcasts.map { it.title })
        assertEquals(listOf("half", "half-older"), grid.podcasts.first().episodes.map { it.song.id })
        assertEquals(0.5f, grid.podcasts.first().progress!!, 0.001f)
        assertEquals(4, grid.shortcuts.size)
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
