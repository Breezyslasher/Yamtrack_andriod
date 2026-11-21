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
     */
    fun appFor(context: Context, link: String): String? {
        val pm = context.packageManager
        fun handlers(uri: String) = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_DEFAULT_ONLY,
            ).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        // Anything that also opens an arbitrary website is a browser.
        val browsers = handlers("https://example.com/").toSet()
        return handlers(link).distinct().firstOrNull { it !in browsers && it != context.packageName }
    }

    /** Whether [provider]'s link opens an installed app (not a browser). */
    fun hasApp(context: Context, provider: WatchProvider): Boolean {
        val link = ProviderLinks.appLink(provider) ?: return false
        return (listOf(link) + ProviderLinks.alternatives(link)).any { appFor(context, it) != null }
    }

    /**
     * Opens [provider]: its app with the link that plays (trying other forms
     * the app may claim), else its web page, else [fallback] (TMDB's page).
     */
    fun open(context: Context, provider: WatchProvider, fallback: String?) {
        ProviderLinks.appLink(provider)?.let { first ->
            for (candidate in listOf(first) + ProviderLinks.alternatives(first)) {
                val pkg = appFor(context, candidate) ?: continue
                // An explicit package skips Android 12+'s verified-domain rule, and a
                // fresh task stops an already-open app from ignoring the link.
                val flags = if (pkg in PLAIN_LAUNCH) Intent.FLAG_ACTIVITY_NEW_TASK
                else Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(candidate)).setPackage(pkg).addFlags(flags)
                if (runCatching { context.startActivity(intent) }.isSuccess) return
            }
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
