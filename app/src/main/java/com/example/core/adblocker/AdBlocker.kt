package com.example.core.adblocker

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlocker {
    private val AD_HOSTS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "mgid.com",
        "popads.net",
        "propellerads.com",
        "adcash.com",
        "amazon-adsystem.com",
        "scorecardresearch.com",
        "zedo.com",
        "adcolony.com",
        "applovin.com",
        "vungle.com",
        "unityads.unity3d.com",
        "smartadserver.com",
        "quantserve.com",
        "moatads.com",
        "yieldmo.com",
        "adroll.com",
        "advertising.com",
        "bidswitch.net",
        "casalemedia.com",
        "inmobi.com",
        "flurry.com"
    )

    private val AD_PATH_KEYWORDS = listOf(
        "/pagead/",
        "/adsbygoogle.js",
        "/show_ads.js",
        "/adserver/",
        "/ad-delivery/",
        "/advertisement/",
        "ads.js",
        "banner_ad",
        "google-analytics.com/analytics.js"
    )

    fun isAd(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false

        // Check if host matches or ends with any ad host
        for (adHost in AD_HOSTS) {
            if (host == adHost || host.endsWith(".$adHost")) {
                return true
            }
        }

        // Check path keywords
        val path = uri.path?.lowercase() ?: ""
        for (keyword in AD_PATH_KEYWORDS) {
            if (path.contains(keyword)) {
                return true
            }
        }

        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    const val CSS_AD_BLOCK_SCRIPT = """
        (function() {
            var css = `
                .ad, .ads, .ad-banner, .advertisement, [id^="google_ads"], 
                [id*="banner-ad"], .sponsored, iframe[src*="ad"], 
                .ad_container, .adsbygoogle, [class*="sponsored-post"], 
                [data-ad-slot], [data-ad-unit] { 
                    display: none !important; 
                    height: 0 !important; 
                    max-height: 0 !important;
                    visibility: hidden !important; 
                    pointer-events: none !important; 
                    opacity: 0 !important;
                }
            `;
            var style = document.createElement('style');
            style.type = 'text/css';
            style.appendChild(document.createTextNode(css));
            (document.head || document.documentElement).appendChild(style);
        })();
    """
}
