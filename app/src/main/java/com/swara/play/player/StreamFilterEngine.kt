package com.swara.play.player

import android.net.Uri
import android.util.Base64

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

    fun buildEnhanceAndDetectJs(
        preferredLangIso: String,
        subtitleEnabled: Boolean,
        subtitleOffsetSec: Float,
        synchronizedVtt: String,
        checkEmptyVideo: Boolean = false
    ): String {
        val lang = preferredLangIso.lowercase().ifBlank { "id" }
        val vttB64 = if (synchronizedVtt.isNotBlank()) {
            Base64.encodeToString(synchronizedVtt.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } else ""
        val enabledStr = if (subtitleEnabled) "true" else "false"
        val offsetStr = String.format(java.util.Locale.US, "%.2f", subtitleOffsetSec)
        val checkEmptyStr = if (checkEmptyVideo) "true" else "false"

        return """
            (function() {
                try {
                    window.open = function() { return null; };
                    var adSel = ['iframe[src*="ads"]','div[id*="ad-"]','div[class*="popup"]','a[target="_blank"][href*="click"]'];
                    adSel.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(el) {
                            if (el && el.parentNode) el.parentNode.removeChild(el);
                        });
                    });

                    if (!document.getElementById('swara-sub-style')) {
                        var style = document.createElement('style');
                        style.id = 'swara-sub-style';
                        style.innerHTML =
                            'video::cue { color: #FFFFFF !important; background-color: rgba(0,0,0,0.52) !important; font-family: sans-serif !important; font-size: 96% !important; font-weight: 600 !important; text-shadow: 0 1px 4px rgba(0,0,0,0.95) !important; line-height: 1.35 !important; } ' +
                            '[class*="subtitle-list"], [class*="subtitles-menu"], [class*="captions-menu"] { display: none !important; }';
                        document.head && document.head.appendChild(style);
                    }

                    window.__swaraSyncSubtitles = function(isEnabled, langCode, offsetSec, vttBase64) {
                        var videos = document.querySelectorAll('video');
                        videos.forEach(function(v) {
                            if (!isEnabled) {
                                if (v.textTracks) {
                                    for (var i = 0; i < v.textTracks.length; i++) v.textTracks[i].mode = 'disabled';
                                }
                                return;
                            }

                            var matchedIdx = -1;
                            if (vttBase64 && vttBase64.length > 10) {
                                var existing = v.querySelector('track[data-swara="1"]');
                                var sig = vttBase64.substring(0, 32);
                                if (!existing || existing.getAttribute('data-sig') !== sig) {
                                    if (existing && existing.parentNode) existing.parentNode.removeChild(existing);
                                    try {
                                        var binStr = atob(vttBase64);
                                        var bytes = new Uint8Array(binStr.length);
                                        for (var b = 0; b < binStr.length; b++) bytes[b] = binStr.charCodeAt(b);
                                        var blob = new Blob([bytes], { type: 'text/vtt;charset=utf-8' });
                                        var blobUrl = URL.createObjectURL(blob);
                                        var trackEl = document.createElement('track');
                                        trackEl.setAttribute('data-swara', '1');
                                        trackEl.setAttribute('data-sig', sig);
                                        trackEl.kind = 'subtitles';
                                        trackEl.label = 'SwaraSync';
                                        trackEl.srclang = langCode;
                                        trackEl.src = blobUrl;
                                        trackEl.default = true;
                                        v.appendChild(trackEl);
                                    } catch (e) {}
                                }
                                if (v.textTracks) {
                                    for (var k = 0; k < v.textTracks.length; k++) {
                                        if (v.textTracks[k].label === 'SwaraSync') matchedIdx = k;
                                    }
                                }
                            }

                            if (matchedIdx === -1 && v.textTracks && v.textTracks.length > 0) {
                                for (var i = 0; i < v.textTracks.length; i++) {
                                    var t = v.textTracks[i];
                                    var tl = (t.language || '').toLowerCase();
                                    var lbl = (t.label || '').toLowerCase();
                                    var ok = (tl === langCode) || (tl.indexOf(langCode) === 0) ||
                                        ((langCode === 'id') && (lbl.indexOf('indo') !== -1 || tl === 'in' || tl === 'ind'));
                                    if (ok && matchedIdx === -1) matchedIdx = i;
                                }
                            }

                            if (v.textTracks && matchedIdx !== -1) {
                                for (var j = 0; j < v.textTracks.length; j++) {
                                    var tr = v.textTracks[j];
                                    tr.mode = (j === matchedIdx) ? 'showing' : 'disabled';
                                    if (j === matchedIdx && tr.cues) {
                                        for (var c = 0; c < tr.cues.length; c++) {
                                            var cue = tr.cues[c];
                                            if (typeof cue.__origStart !== 'number') {
                                                cue.__origStart = cue.startTime;
                                                cue.__origEnd = cue.endTime;
                                            }
                                            var ns = Math.max(0, cue.__origStart + offsetSec);
                                            var ne = Math.max(ns + 0.2, cue.__origEnd + offsetSec);
                                            if (Math.abs(cue.startTime - ns) > 0.05) {
                                                cue.startTime = ns;
                                                cue.endTime = ne;
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    };

                    window.__swaraSyncSubtitles($enabledStr, '$lang', $offsetStr, '$vttB64');
                    setTimeout(function() { window.__swaraSyncSubtitles($enabledStr, '$lang', $offsetStr, '$vttB64'); }, 2200);
                    setTimeout(function() { window.__swaraSyncSubtitles($enabledStr, '$lang', $offsetStr, '$vttB64'); }, 5000);

                    var bodyText = (document.body && document.body.innerText) ? document.body.innerText.toLowerCase() : '';
                    var errorSignals = [
                        'error code 522', 'connection timed out', 'error code 521', 'error code 502', 'error code 520',
                        "we couldn't find this content", 'media not found', 'video not available', '404 not found',
                        'cannot be processed', 'unable to process', 'tidak dapat diproses', 'no sources found',
                        'file was deleted', 'this video is unavailable', 'episode not found', 'source not found',
                        'just a moment', 'checking your browser', 'access denied', 'forbidden'
                    ];
                    for (var eIdx = 0; eIdx < errorSignals.length; eIdx++) {
                        if (bodyText.indexOf(errorSignals[eIdx]) !== -1) return 'STREAM_ERROR';
                    }

                    if ($checkEmptyStr) {
                        var vids = document.querySelectorAll('video');
                        var iframes = document.querySelectorAll('iframe');
                        if (vids.length === 0 && iframes.length === 0) return 'STREAM_ERROR';
                        if (vids.length > 0 && iframes.length === 0) {
                            var anyReady = false;
                            for (var vi = 0; vi < vids.length; vi++) {
                                if (vids[vi].readyState > 0 || (vids[vi].currentSrc && vids[vi].currentSrc.length > 5)) anyReady = true;
                            }
                            if (!anyReady) return 'STREAM_ERROR';
                        }
                    }
                } catch (e) {}
                return 'OK';
            })();
        """
    }

    fun buildPlayerControlJs(action: String, value: String = ""): String {
        return when (action) {
            "skip" -> "(function(){document.querySelectorAll('video').forEach(function(v){v.currentTime=Math.max(0,(v.currentTime||0)+($value));});})();"
            "speed" -> "(function(){document.querySelectorAll('video').forEach(function(v){v.playbackRate=$value;});})();"
            "fit" -> "(function(){document.querySelectorAll('video').forEach(function(v){v.style.objectFit='$value';});})();"
            "playpause" -> "(function(){document.querySelectorAll('video').forEach(function(v){if(v.paused)v.play();else v.pause();});})();"
            else -> ""
        }
    }
}
