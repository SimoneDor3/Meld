package com.metrolist.innertube

import com.metrolist.innertube.utils.sapisidAuthorization
import io.ktor.http.Url
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WatchtimeTest {
    private val baseUrl =
        "https://s.youtube.com/api/stats/watchtime?cl=812345678&docid=dQw4w9WgXcQ&ei=AbCdEfGhIjKlMnOp" +
            "&fexp=v1%2C24004644&ns=yt&plid=AAYBbbcccDDDeee&el=detailpage&len=1800&of=Q1w2E3r4T5y6U7i8" +
            "&vm=CAEQARgEKiBkXzJ1%3D"

    private fun watchtimeUrl(
        lengthSec: Double = 1799.877,
        state: WatchtimeState = WatchtimeState.PLAYING,
        final: Boolean = false,
    ) = buildWatchtimeUrl(
        baseUrl = baseUrl,
        cpn = "abcdefgh-_123456",
        segmentStartSec = 600.0,
        segmentEndSec = 640.5,
        positionSec = 640.5,
        lengthSec = lengthSec,
        elapsedRealSec = 40.25,
        state = state,
        final = final,
    )

    @Test
    fun `keeps the player's tracking params verbatim and appends the watched segment`() {
        assertEquals(
            "https://s.youtube.com/api/stats/watchtime?cl=812345678&docid=dQw4w9WgXcQ&ei=AbCdEfGhIjKlMnOp" +
                "&fexp=v1%2C24004644&ns=yt&plid=AAYBbbcccDDDeee&el=detailpage&len=1799.877&of=Q1w2E3r4T5y6U7i8" +
                "&vm=CAEQARgEKiBkXzJ1%3D&ver=2&c=WEB_REMIX&cpn=abcdefgh-_123456&cmt=640.500&st=600.000" +
                "&et=640.500&rt=40.250&lact=1&state=playing&volume=100&muted=0",
            watchtimeUrl(),
        )
    }

    @Test
    fun `final ping carries the flag and the paused state`() {
        val parameters = Url(watchtimeUrl(state = WatchtimeState.PAUSED, final = true)).parameters

        assertEquals("1", parameters["final"])
        assertEquals("paused", parameters["state"])
        assertNull(Url(watchtimeUrl()).parameters["final"])
    }

    @Test
    fun `unknown length keeps the player's len`() {
        assertEquals(listOf("1800"), Url(watchtimeUrl(lengthSec = 0.0)).parameters.getAll("len"))
    }

    @Test
    fun `media times keep a decimal point in comma locales`() {
        val defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.ITALY)
        try {
            assertEquals("640.500", Url(watchtimeUrl()).parameters["cmt"])
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun `sapisid authorization signs timestamp, cookie and origin`() {
        assertEquals(
            "SAPISIDHASH 1700000000_163e661c1ad4177128b563dfbe8b0ebfd1296a14",
            sapisidAuthorization("AbCdEfGhIjKlMnOp/QrStUvWxYz012345", "https://music.youtube.com", 1_700_000_000),
        )
    }
}
