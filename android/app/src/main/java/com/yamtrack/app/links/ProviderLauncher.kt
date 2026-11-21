package com.yamtrack.app.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.yamtrack.app.R
import com.yamtrack.app.data.model.WatchProvider

/**
 * Opens a streaming service's link in its app so the title starts playing,
 * else the web page in a browser.
 */
object ProviderLauncher {

    /** Apps whose player opens then closes when launched into a cleared task. */
    private val PLAIN_LAUNCH = setOf("com.wbd.stream", "com.discovery.discoveryplus.mobile")

    /**
     * The installed app, not a browser, that claims [link]; null when only
     * browsers do. Needs the manifest's `<queries>` on Android 11+.
     *
     * MATCH_ALL: on Android 12+ a query for a web link otherwise leaves out
     * apps whose links aren't verified on this phone (often streaming apps,
     * e.g. Disney+), returning only browsers, so the app looked uninstalled
     * and the link opened in the browser. Launching names the package
     * (setPackage), which that rule doesn't apply to.
     */
    fun appFor(context: Context, link: String): String? {
        val pm = context.packageManager
        fun handlers(uri: String) = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL,
            ).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        // Anything that also opens an arbitrary website is a browser.
        val browsers = handlers("https://example.com/").toSet()
        return handlers(link).distinct().firstOrNull { it !in browsers && it != context.packageName }
    }

    /** Whether [pkg] is installed (seen through the manifest's launcher `<queries>`). */
    private fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    /**
     * The links to try and the app for each, best first: apps that say they
     * open a link, then each link's known service app when it's installed,
     * as some (Disney+) don't list every path they take.
     */
    private fun attempts(context: Context, provider: WatchProvider): List<Pair<String, String>> {
        val links = ProviderLinks.appCandidates(provider)
        val claimed = links.mapNotNull { link -> appFor(context, link)?.let { link to it } }
        val known = links.mapNotNull { link ->
            ProviderLinks.knownApp(link)?.takeIf { installed(context, it) }?.let { link to it }
        }
        return (claimed + known).distinct()
    }

    /** Whether [provider]'s link opens an installed app (not a browser). */
    fun hasApp(context: Context, provider: WatchProvider): Boolean = attempts(context, provider).isNotEmpty()

    /**
     * Opens [provider]: its app with the link that plays (trying the TV link,
     * the web link and other forms the app may take), else its web page,
     * else [fallback] (TMDB's page).
     */
    fun open(context: Context, provider: WatchProvider, fallback: String?) {
        for ((link, pkg) in attempts(context, provider)) {
            // An explicit package skips Android 12+'s verified-domain rule, and a
            // fresh task stops an already-open app from ignoring the link.
            val flags = if (pkg in PLAIN_LAUNCH) Intent.FLAG_ACTIVITY_NEW_TASK
            else Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg).addFlags(flags)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
        // No app takes any form of it: the web page in a browser.
        openInBrowser(context, ProviderLinks.webLink(provider) ?: fallback)
    }

    fun openInBrowser(context: Context, link: String?) {
        link ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, R.string.could_not_open_browser, Toast.LENGTH_SHORT).show() }
    }

    /**
     * Services to show, best first: deduplicated, with Discovery+ movie links
     * made playable, those opening an installed app first and add-on channels
     * last. Calls the package manager, so run it off the main thread.
     */
    fun arrange(context: Context, providers: List<WatchProvider>): List<WatchProvider> =
        arrangeWithApps(context, providers).providers

    /** [providers] in [arrange]'s order, and which of them open an installed app. */
    class Arranged(val providers: List<WatchProvider>, val withApp: Set<WatchProvider>)

    /** [arrange], also saying which services open an installed app. Off the main thread. */
    fun arrangeWithApps(context: Context, providers: List<WatchProvider>): Arranged {
        val list = ProviderLinks.dedupe(ProviderLinks.discoveryPlayLinks(providers))
        val withApp = list.filter { hasApp(context, it) }.toSet()
        val sorted = list.sortedWith(compareBy({ it !in withApp }, { ProviderLinks.isChannel(it) }))
        return Arranged(sorted, withApp)
    }
}
