package com.swara.play.player

import android.net.Uri

object StreamFilterEngine {
    private val safeVideoExtensions = listOf(".m3u8", ".mp4", ".ts", ".m4s", ".mpd", ".vtt", ".srt", ".key")

    private val blockedHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "highperformanceformat.com",
        "exoclick.com", "juicyads.com", "trafficjunky.net", "hilltopads.net", "clickadu.com",
        "onclickalgo.com", "acscdn.com", "adsco.re", "adskeeper.com", "mgid.com", "taboola.com",
        "outbrain.com", "histats.com", "mc.yandex.ru", "a-ads.com", "bidvertiser.com", "monetag.com",
        "pushame.com", "notifpush.com", "ad-maven.com", "admaven.com", "bet365.com", "1xbet.com",
        "melbet.com", "realsrv.com", "tsyndicate.com", "dtscout.com", "whos.amung.us"
    )

    private val blockedKeywords = listOf(
        "popunder", "popads", "adserver", "ad_banner", "doubleclick", "vast.xml",
        "pagead", "/ads/", "banner_ad", "clicktracker", "redirect_ad"
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

    fun isDownloadableMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains(".mp4") || lower.contains(".m3u8")) &&
            !lower.contains("ads") && !lower.contains("doubleclick") && !lower.contains("blank")
    }

    fun buildEnhanceAndDetectJs(preferredLangIso: String): String {
        val lang = preferredLangIso.lowercase().ifBlank { "id" }
        return """
            (function() {
                try {
                    window.open = function() { return null; };
                    var selectors = ['iframe[src*="ads"]','div[id*="ad-"]','div[class*="popup"]','a[target="_blank"][href*="click"]'];
                    selectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(el) {
                            if (el && el.parentNode) el.parentNode.removeChild(el);
                        });
                    });

                    // Inject minimalist readable subtitle styling
                    if (!document.getElementById('swara-sub-style')) {
                        var style = document.createElement('style');
                        style.id = 'swara-sub-style';
                        style.innerHTML = 'video::cue { color: #FFFFFF !important; background-color: rgba(0,0,0,0.48) !important; font-family: sans-serif !important; font-weight: 600 !important; text-shadow: 0 1px 4px rgba(0,0,0,0.95) !important; line-height: 1.35 !important; }';
                        document.head && document.head.appendChild(style);
                    }

                    // Select ONLY ONE synchronized subtitle track matching device language
                    function syncSingleSubtitle() {
                        var videos = document.querySelectorAll('video');
                        videos.forEach(function(v) {
                            if (v.textTracks && v.textTracks.length > 0) {
                                var matchedIndex = -1;
                                for (var i = 0; i < v.textTracks.length; i++) {
                                    var t = v.textTracks[i];
                                    var tl = (t.language || '').toLowerCase();
                                    var lbl = (t.label || '').toLowerCase();
                                    var isMatch = (tl === '$lang') || (tl.indexOf('$lang') === 0) ||
                                        (('$lang' === 'id') && (lbl.indexOf('indo') !== -1 || tl === 'in' || tl === 'ind'));
                                    if (isMatch && matchedIndex === -1) {
                                        matchedIndex = i;
                                    }
                                }
                                if (matchedIndex !== -1) {
                                    for (var j = 0; j < v.textTracks.length; j++) {
                                        v.textTracks[j].mode = (j === matchedIndex) ? 'showing' : 'disabled';
                                    }
                                }
                            }
                        });
                    }
                    syncSingleSubtitle();
                    setTimeout(syncSingleSubtitle, 1800);
                    setTimeout(syncSingleSubtitle, 4500);

                    var bodyText = (document.body && document.body.innerText) ? document.body.innerText.toLowerCase() : '';
                    if (bodyText.indexOf('error code 522') !== -1 ||
                        bodyText.indexOf('connection timed out') !== -1 ||
                        bodyText.indexOf('error code 521') !== -1 ||
                        bodyText.indexOf('error code 502') !== -1 ||
                        bodyText.indexOf('error code 520') !== -1 ||
                        bodyText.indexOf("we couldn't find this content") !== -1 ||
                        bodyText.indexOf('media not found') !== -1 ||
                        bodyText.indexOf('video not available') !== -1 ||
                        bodyText.indexOf('404 not found') !== -1) {
                        return 'STREAM_ERROR';
                    }
                } catch (e) {}
                return 'OK';
            })();
        """
    }
}
