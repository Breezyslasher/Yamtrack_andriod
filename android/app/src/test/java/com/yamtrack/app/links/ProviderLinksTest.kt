package com.yamtrack.app.links

import com.yamtrack.app.data.model.WatchProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real JustWatch link shapes and what each service's phone app plays. */
class ProviderLinksTest {

    private val uuidA = "c07ae042-1111-4a2b-9c3d-0123456789ab"
    private val uuidB = "50b6e1f2-2222-4b3c-8d4e-abcdef012345"

    @Test fun hboMaxMovesToThePlayHostLowercased() {
        assertEquals(
            "https://play.hbomax.com/video/watch/$uuidA/$uuidB",
            ProviderLinks.normalize("https://www.hbomax.com/video/watch/${uuidA.uppercase()}/$uuidB"),
        )
        assertEquals(
            "https://play.hbomax.com/video/watch/$uuidA",
            ProviderLinks.normalize("https://www.max.com/video/watch/$uuidA?utm_source=justwatch"),
        )
    }

    @Test fun discoveryPlusMovesToThePlayHost() {
        assertEquals(
            "https://play.discoveryplus.com/video/watch/$uuidA",
            ProviderLinks.normalize("https://www.discoveryplus.com/video/watch/$uuidA"),
        )
    }

    @Test fun disneyEntityPagesBecomePlayLinks() {
        assertEquals(
            "https://www.disneyplus.com/play/$uuidA",
            ProviderLinks.normalize("https://www.disneyplus.com/en-gb/browse/entity-$uuidA"),
        )
    }

    @Test fun netflixTitlePagesBecomeWatchLinks() {
        assertEquals("https://www.netflix.com/watch/80057281",
            ProviderLinks.normalize("https://www.netflix.com/title/80057281"))
        assertEquals("https://www.netflix.com/watch/80057281",
            ProviderLinks.normalize("https://www.netflix.com/gb/title/80057281?source=jw"))
    }

    @Test fun appleTvMoviesGetActionPlay() {
        assertEquals(
            "https://tv.apple.com/us/movie/f1/umc.cmc.3t6dvnnr87zwd4wmvpdx5came?action=play",
            ProviderLinks.normalize("https://tv.apple.com/us/movie/f1/umc.cmc.3t6dvnnr87zwd4wmvpdx5came?utm_source=justwatch"),
        )
    }

    @Test fun appleTvEpisodesKeepShowAndPlayableIds() {
        assertEquals(
            "https://tv.apple.com/us/episode/x/umc.cmc.1?showId=umc.cmc.2&playableId=tvs.sbd.3&action=play",
            ProviderLinks.normalize(
                "https://tv.apple.com/us/episode/x/umc.cmc.1?showId=umc.cmc.2&playableId=tvs.sbd.3&utm_source=jw"),
        )
    }

    @Test fun affiliateRedirectsAreUnwrapped() {
        assertEquals(
            "https://www.crunchyroll.com/watch/G31UXN249/slug",
            ProviderLinks.normalize(
                "https://crunchyroll.pxf.io/xk92Nv?u=https%3A%2F%2Fwww.crunchyroll.com%2Fwatch%2FG31UXN249%2Fslug&subId3=x"),
        )
    }

    @Test fun disneyAffiliateRedirectsAreUnwrapped() {
        val target = "https://www.disneyplus.com/browse/entity-$uuidA"
        assertEquals(
            "https://www.disneyplus.com/play/$uuidA",
            ProviderLinks.normalize(
                "https://disneyplus.bn5x.net/c/1/2/3?u=" + java.net.URLEncoder.encode(target, "UTF-8")),
        )
        assertEquals(
            "https://www.disneyplus.com/play/$uuidA",
            ProviderLinks.normalize("https://www.disneyplus.com/en-GB/browse/entity-$uuidA"),
        )
    }

    @Test fun aLinkToTheSameSiteIsNotARedirect() {
        assertNull(ProviderLinks.unwrap("https://www.example.com/x?u=https%3A%2F%2Fwww.example.com%2Fy"))
        assertNull(ProviderLinks.unwrap("https://tubitv.com/movies/123?u=abc"))
    }

    @Test fun theWebLinkIsTriedAfterTheTvLink() {
        val p = WatchProvider(
            link = "https://www.disneyplus.com/browse/entity-$uuidA",
            tvLink = "https://www.disneyplus.com/series/andor/3jLIGMDYINqD",
        )
        assertEquals(
            listOf("https://www.disneyplus.com/series/andor/3jLIGMDYINqD", "https://www.disneyplus.com/play/$uuidA"),
            ProviderLinks.appCandidates(p),
        )
    }

    @Test fun servicesAppsAreKnownBySite() {
        assertEquals("com.disney.disneyplus", ProviderLinks.knownApp("https://www.disneyplus.com/play/$uuidA"))
        assertEquals("com.wbd.stream", ProviderLinks.knownApp("https://play.hbomax.com/video/watch/$uuidA"))
        assertNull(ProviderLinks.knownApp("https://example.com/"))
        assertNull(ProviderLinks.knownApp("https://notdisneyplus.com/"))
    }

    @Test fun peacockDetailDeeplinksBecomePlay() {
        val pdp = "https://www.peacocktv.com/deeplink?deeplinkData=" +
            "%7B%22pvid%22%3A%22abc%22%2C%22type%22%3A%22PROGRAMME%22%2C%22action%22%3A%22PDP%22%7D"
        assertEquals(ProviderLinks.peacockPlay("abc", "PROGRAMME"), ProviderLinks.normalize(pdp))
        assertTrue(ProviderLinks.peacockPlay("abc", "PROGRAMME").contains("%22action%22%3A%22PLAY%22"))
    }

    @Test fun peacockWebPagesBecomePlayDeeplinks() {
        assertEquals(
            ProviderLinks.peacockPlay(uuidA, "PROGRAMME"),
            ProviderLinks.normalize("https://www.peacocktv.com/watch/asset/movies/the-grinch/$uuidA"),
        )
        assertEquals(
            ProviderLinks.peacockPlay(uuidA, "EPISODE"),
            ProviderLinks.normalize("https://www.peacocktv.com/watch/asset/tv/the-office/episodes/s1-e1/$uuidA"),
        )
    }

    @Test fun starzWebLinksBecomePlayLinks() {
        assertEquals("https://www.starz.com/us/en/play/12345",
            ProviderLinks.normalize("https://www.starz.com/us/en/movies/12345"))
    }

    @Test fun starzTvLinksHavePhoneAlternatives() {
        assertEquals(
            listOf("https://www.starz.com/us/en/play/222", "https://www.starz.com/us/en/series/111/episodes/222"),
            ProviderLinks.alternatives("https://www.starz.com/series/111/episodes/222"),
        )
        assertEquals(
            listOf("https://www.starz.com/us/en/play/9", "https://www.starz.com/us/en/movies/9"),
            ProviderLinks.alternatives("https://www.starz.com/movies/9"),
        )
        assertTrue(ProviderLinks.alternatives("https://tubitv.com/movies/123").isEmpty())
    }

    @Test fun crunchyrollLinksAreCleaned() {
        assertEquals("https://www.crunchyroll.com/watch/G31UXN249/slug",
            ProviderLinks.normalize("https://www.crunchyroll.com/de/watch/G31UXN249/slug?utm=1"))
    }

    @Test fun paramountMoviesOpenInBrowsersWithOpen() {
        assertEquals("https://www.paramountplus.com/movies/video/ABC/#open",
            ProviderLinks.browserLink("https://www.paramountplus.com/movies/video/ABC/"))
        assertEquals("https://www.paramountplus.com/shows/video/ABC/",
            ProviderLinks.browserLink("https://www.paramountplus.com/shows/video/ABC/"))
    }

    @Test fun otherLinksAreUnchanged() {
        assertEquals("https://tubitv.com/movies/123", ProviderLinks.normalize("https://tubitv.com/movies/123"))
    }

    @Test fun tvLinkIsPreferredForTheApp() {
        val p = WatchProvider(link = "https://www.netflix.com/title/1", tvLink = "https://www.netflix.com/watch/2")
        assertEquals("https://www.netflix.com/watch/2", ProviderLinks.appLink(p))
        assertEquals("https://www.netflix.com/watch/1", ProviderLinks.webLink(p))
        assertNull(ProviderLinks.appLink(WatchProvider(link = " ")))
    }

    @Test fun discoveryMoviesPlayTheHboMaxVideo() {
        val hbo = WatchProvider(providerName = "HBO Max", link = "https://play.hbomax.com/video/watch/$uuidA")
        val discovery = WatchProvider(providerName = "Discovery+", link = "https://www.discoveryplus.com/movie/$uuidB")
        val result = ProviderLinks.discoveryPlayLinks(listOf(hbo, discovery))
        assertEquals("https://play.discoveryplus.com/video/watch/$uuidA", result[1].tvLink)
        assertEquals(hbo, result[0])
    }

    @Test fun channelsAreRecognised() {
        assertTrue(ProviderLinks.isChannel(WatchProvider(providerName = "Starz Amazon Channel")))
        assertFalse(ProviderLinks.isChannel(WatchProvider(providerName = "The Roku Channel")))
        assertFalse(ProviderLinks.isChannel(WatchProvider(providerName = "Netflix")))
    }

    @Test fun linksSharedByTwoEpisodesAreTheShows() {
        val show = WatchProvider(providerName = "Pluto TV", link = "https://pluto.tv/on-demand/series/x")
        val e1 = WatchProvider(providerName = "Tubi", link = "https://tubitv.com/tv-shows/1")
        val e2 = WatchProvider(providerName = "Tubi", link = "https://tubitv.com/tv-shows/2")
        assertEquals(setOf("https://pluto.tv/on-demand/series/x"),
            ProviderLinks.sharedLinks(listOf(show, e1), listOf(show, e2)))
    }
}
