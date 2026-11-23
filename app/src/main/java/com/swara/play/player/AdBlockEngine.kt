package com.swara.play.player

import android.net.Uri

object AdBlockEngine {
    private val safeVideoExtensions = listOf(".m3u8", ".mp4", ".ts", ".m4s", ".mpd", ".vtt", ".srt", ".key")

    private val blockedHosts = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "highperformanceformat.com",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "hilltopads.net",
        "clickadu.com",
        "onclickalgo.com",
        "acscdn.com",
        "adsco.re",
        "adskeeper.com",
        "mgid.com",
        "taboola.com",
        "outbrain.com",
        "histats.com",
        "mc.yandex.ru",
        "a-ads.com",
        "bidvertiser.com",
        "monetag.com",
        "pushame.com",
        "notifpush.com",
        "ad-maven.com",
        "admaven.com",
        "bet365.com",
        "1xbet.com",
        "melbet.com",
        "realsrv.com",
        "tsyndicate.com",
        "dtscout.com",
        "whos.amung.us"
    )

    private val blockedKeywords = listOf(
        "popunder",
        "popads",
        "adserver",
        "ad_banner",
        "doubleclick",
        "vast.xml",
        "pagead",
        "/ads/",
        "banner_ad",
        "clicktracker",
        "redirect_ad"
    )

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        if (safeVideoExtensions.any { lower.contains(it) }) return false
        val host = runCatching { Uri.parse(lower).host.orEmpty() }.getOrDefault("")
        if (host.isNotBlank()) {
            for (blocked in blockedHosts) {
                if (host == blocked || host.endsWith(".$blocked")) return true
            }
        }
        return blockedKeywords.any { lower.contains(it) }
    }

    const val CLEANUP_AND_DETECT_ERROR_JS = """
        (function() {
            try {
                window.open = function() { return null; };
                var selectors = [
                    'iframe[src*="ads"]',
                    'div[id*="ad-"]',
                    'div[class*="popup"]',
                    'div[class*="ad-banner"]',
                    'a[target="_blank"][href*="click"]'
                ];
                selectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el && el.parentNode) el.parentNode.removeChild(el);
                    });
                });
                var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
                if (bodyText.indexOf('Error code 522') !== -1 ||
                    bodyText.indexOf('Connection timed out') !== -1 ||
                    bodyText.indexOf('Error code 521') !== -1 ||
                    bodyText.indexOf('Error code 502') !== -1 ||
                    bodyText.indexOf('Error code 520') !== -1) {
                    return 'CF_ERROR';
                }
            } catch (e) {}
            return 'OK';
        })();
    """
}
