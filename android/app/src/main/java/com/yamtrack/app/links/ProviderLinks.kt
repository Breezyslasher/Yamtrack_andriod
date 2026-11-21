package com.yamtrack.app.links

import com.yamtrack.app.data.model.WatchProvider
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Turns the JustWatch links the server sends for each streaming service into
 * the forms the services' phone apps open and *play*, rather than showing the
 * title's page or handing the link to the browser.
 *
 * Plain Kotlin (no Android types) so it is covered by JVM unit tests. The
 * rewrites follow what was tested on real phones; see ProviderLinksTest.
 */
object ProviderLinks {
    /** The rest of a URL (path, query, fragment): ignored, as it is tracking. */
    private const val Q = "(?:[/?#].*)?$"
    private const val UUID = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"

    /** Any link with a query, for affiliate redirects: host, then the query. */
    private val REDIRECT = Regex("""^https?://([^/?#]+)[^?#]*\?([^#]*)(?:#.*)?$""")
    private val HOST = Regex("""^https?://([^/?#]+)""")
    private val HBO = Regex("^https?://(?:www\\.|play\\.)?(?:hbo)?max\\.com/video/watch/($UUID(?:/$UUID)?)$Q")
    private val DISCOVERY = Regex("^https?://(?:www\\.|play\\.)?discoveryplus\\.com/video/watch/($UUID(?:/$UUID)?)$Q")
    private val DISNEY = Regex("^https?://(?:www\\.)?disneyplus\\.com/(?:[a-zA-Z]{2}-[a-zA-Z]{2}/)?(?:play/|browse/entity-)$UUID$Q")
    private val NETFLIX = Regex("^https?://(?:www\\.)?netflix\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:title|watch)/(\\d+)$Q")
    private val STARZ = Regex("^https?://(?:www\\.)?starz\\.com/([a-z]{2})/([a-z]{2})/(?:play|movies)/(\\d+)$Q")
    private val STARZ_TV = Regex("^https?://(?:www\\.)?starz\\.com/(movies|series)/(\\d+)(/episodes/(\\d+))?$Q")
    private val APPLE_MOVIE = Regex("^https?://tv\\.apple\\.com/([a-z]{2})/movie/([^/?#]+)/(umc\\.[A-Za-z0-9.]+)$Q")
    private val APPLE_EPISODE = Regex("^https?://tv\\.apple\\.com/([a-z]{2})/episode/([^/?#]+)/(umc\\.[A-Za-z0-9.]+)(?:\\?([^#]*))?(?:#.*)?$")
    private val PEACOCK_DEEP = Regex("^https?://(?:www\\.)?peacocktv\\.com/deeplink\\?deeplinkData=([^&#]+).*$")
    private val PEACOCK_ASSET = Regex("^https?://(?:www\\.)?peacocktv\\.com/watch/asset/(movies|tv)/([^?#]*)/([0-9a-f-]{36})$Q")
    private val CRUNCHYROLL = Regex("^https?://(?:www\\.)?crunchyroll\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?watch/([A-Z0-9]+)(/[^?#]*)?$Q")
    private val PARAMOUNT_MOVIE = Regex("""paramountplus\.com/movies/video/[^/?#]+/?""")
    private val JSON_FIELD = { name: String -> Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"") }

    /** The link to open in the service's app: `tv_link` (starts the title), else `link`. */
    fun appLink(p: WatchProvider): String? =
        (p.tvLink ?: p.link)?.takeIf { it.isNotBlank() }?.let(::normalize)

    /**
     * Every link to try in the service's app, best first: `tv_link` and `link`
     * in their playing forms, each followed by its [alternatives]. A TV link
     * the phone app doesn't claim falls back to the web link that it may.
     */
    fun appCandidates(p: WatchProvider): List<String> =
        listOfNotNull(p.tvLink, p.link)
            .filter { it.isNotBlank() }
            .map(::normalize)
            .flatMap { listOf(it) + alternatives(it) }
            .distinct()

    /** Phone apps of services, by the sites their links are on. */
    private val APPS = listOf(
        "disneyplus.com" to "com.disney.disneyplus",
        "netflix.com" to "com.netflix.mediaclient",
        "hbomax.com" to "com.wbd.stream",
        "max.com" to "com.wbd.stream",
        "discoveryplus.com" to "com.discovery.discoveryplus.mobile",
        "hulu.com" to "com.hulu.plus",
        "peacocktv.com" to "com.peacocktv.peacockandroid",
        "paramountplus.com" to "com.cbs.app",
        "crunchyroll.com" to "com.crunchyroll.crunchyroid",
        "starz.com" to "com.bydeluxe.d3.android.program.starz",
        "tubitv.com" to "com.tubitv",
        "pluto.tv" to "tv.pluto.android",
    )

    /**
     * The service's phone app for [link]'s site, or null. Tried by package
     * when the app doesn't say it opens the link (its own link filter
     * doesn't list that path), as it may still take it.
     */
    fun knownApp(link: String): String? {
        val host = HOST.find(link)?.groupValues?.get(1)?.lowercase() ?: return null
        return APPS.firstOrNull { (site, _) -> host == site || host.endsWith(".$site") }?.second
    }

    /** The link to open in a browser when no app claims one: the web link. */
    fun webLink(p: WatchProvider): String? =
        (p.link ?: p.tvLink)?.takeIf { it.isNotBlank() }?.let { browserLink(normalize(it)) }

    /** [raw] in the form the service's phone app opens and plays. */
    fun normalize(raw: String): String {
        val text = raw.trim()
        unwrap(text)?.let { return normalize(it) }
        DISNEY.matchEntire(text)?.let { return "https://www.disneyplus.com/play/${it.groupValues[1].lowercase()}" }
        STARZ.matchEntire(text)?.let {
            val g = it.groupValues
            return "https://www.starz.com/${g[1]}/${g[2]}/play/${g[3]}"
        }
        NETFLIX.matchEntire(text)?.let { return "https://www.netflix.com/watch/${it.groupValues[1]}" }
        HBO.matchEntire(text)?.let { return "https://play.hbomax.com/video/watch/${it.groupValues[1].lowercase()}" }
        DISCOVERY.matchEntire(text)?.let {
            return "https://play.discoveryplus.com/video/watch/${it.groupValues[1].lowercase()}"
        }
        APPLE_MOVIE.matchEntire(text)?.let {
            val g = it.groupValues
            return "https://tv.apple.com/${g[1]}/movie/${g[2]}/${g[3]}?action=play"
        }
        APPLE_EPISODE.matchEntire(text)?.let {
            val g = it.groupValues
            val keep = g[4].split('&').filter { kv -> kv.startsWith("showId=") || kv.startsWith("playableId=") }
            return "https://tv.apple.com/${g[1]}/episode/${g[2]}/${g[3]}?" + (keep + "action=play").joinToString("&")
        }
        PEACOCK_DEEP.matchEntire(text)?.let { match ->
            val data = runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }.getOrNull() ?: return text
            val pvid = JSON_FIELD("pvid").find(data)?.groupValues?.get(1) ?: return text
            val type = JSON_FIELD("type").find(data)?.groupValues?.get(1) ?: return text
            return peacockPlay(pvid, type)
        }
        PEACOCK_ASSET.matchEntire(text)?.let {
            val (kind, path, pvid) = it.destructured
            return when {
                kind == "movies" -> peacockPlay(pvid, "PROGRAMME")
                "/episodes/" in "/$path/" -> peacockPlay(pvid, "EPISODE")
                else -> text
            }
        }
        CRUNCHYROLL.matchEntire(text)?.let {
            return "https://www.crunchyroll.com/watch/${it.groupValues[1]}${it.groupValues[2]}"
        }
        return text
    }

    /**
     * The real link inside an affiliate redirect, or null: a `u=` (or `url=`)
     * parameter holding a link to another site, as with Impact's pxf.io,
     * sjv.io and bn5x.net (Disney+) hosts.
     */
    fun unwrap(link: String): String? {
        val m = REDIRECT.matchEntire(link) ?: return null
        val host = m.groupValues[1].lowercase()
        val param = m.groupValues[2].split('&')
            .firstOrNull { it.startsWith("u=") || it.startsWith("url=") } ?: return null
        val target = runCatching { URLDecoder.decode(param.substringAfter('='), "UTF-8") }.getOrNull()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        val targetHost = HOST.find(target)?.groupValues?.get(1)?.lowercase() ?: return null
        return target.takeIf { targetHost != host }
    }

    /** Peacock's deeplink that plays [pvid] rather than showing its page. */
    fun peacockPlay(pvid: String, type: String): String =
        "https://www.peacocktv.com/deeplink?deeplinkData=" +
            URLEncoder.encode("""{"pvid":"$pvid","type":"$type","action":"PLAY"}""", "UTF-8")

    /**
     * Other forms of [link] for a phone app that doesn't take it as given, best
     * first. Starz's TV links (`/movies/<id>`, `/series/<sid>/episodes/<eid>`)
     * aren't claimed by its phone app, which takes `/us/en/play/<id>`.
     */
    fun alternatives(link: String): List<String> {
        STARZ_TV.matchEntire(link)?.let { m ->
            val (kind, id, episodePath, episodeId) = m.destructured
            return if (kind == "movies") {
                listOf("https://www.starz.com/us/en/play/$id", "https://www.starz.com/us/en/movies/$id")
            } else {
                val page = "https://www.starz.com/us/en/series/$id$episodePath"
                if (episodeId.isNotEmpty()) listOf("https://www.starz.com/us/en/play/$episodeId", page) else listOf(page)
            }
        }
        return emptyList()
    }

    /** What a browser needs: Paramount+ movie pages only play with `#open`. */
    fun browserLink(link: String): String =
        if (PARAMOUNT_MOVIE.containsMatchIn(link) && !link.contains("#")) "$link#open" else link

    private val HBO_VIDEO = Regex("""(?:hbo)?max\.com/video/watch/([0-9a-fA-F-]{36})""")
    private val DISCOVERY_MOVIE = Regex("""discoveryplus\.com/movie/[0-9a-fA-F-]{36}""")

    /**
     * A Discovery+ movie link is a page that doesn't play. HBO Max and
     * Discovery+ share one catalog, so when the list also has an HBO Max video
     * link, Discovery+ plays that same video id.
     */
    fun discoveryPlayLinks(providers: List<WatchProvider>): List<WatchProvider> {
        val video = providers.firstNotNullOfOrNull { p ->
            listOfNotNull(p.tvLink, p.link).firstNotNullOfOrNull { HBO_VIDEO.find(it)?.groupValues?.get(1) }
        } ?: return providers
        val play = "https://play.discoveryplus.com/video/watch/${video.lowercase()}"
        return providers.map { p ->
            if (listOfNotNull(p.tvLink, p.link).any { DISCOVERY_MOVIE.containsMatchIn(it) }) p.copy(tvLink = play) else p
        }
    }

    /** Add-on channels sold inside another app ("Starz Amazon Channel"). */
    fun isChannel(p: WatchProvider): Boolean {
        val name = p.providerName.orEmpty()
        return name.endsWith("Channel", ignoreCase = true) && !name.equals("The Roku Channel", ignoreCase = true)
    }

    /**
     * Links two episodes share are the show's page, not either episode's own
     * (Pluto TV gives some shows one link for every episode).
     */
    fun sharedLinks(a: List<WatchProvider>, b: List<WatchProvider>): Set<String> {
        fun links(list: List<WatchProvider>) = list.mapNotNull { it.tvLink ?: it.link }.toSet()
        return links(a) intersect links(b)
    }

    /** The same service and link listed twice counts once. */
    fun dedupe(providers: List<WatchProvider>): List<WatchProvider> =
        providers.distinctBy { appLink(it) ?: "${it.providerId}|${it.providerName}" }
}
