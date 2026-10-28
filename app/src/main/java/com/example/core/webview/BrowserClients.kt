package com.example.core.webview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.core.adblocker.AdBlocker
import com.example.core.sniffer.MediaSniffer

class BrowserWebViewClient(
    private val context: Context,
    private val isAdBlockEnabled: () -> Boolean,
    private val isIncognito: () -> Boolean,
    private val isDarkMode: () -> Boolean,
    private val mediaSniffer: MediaSniffer,
    private val onPageStartedCallback: (url: String) -> Unit,
    private val onPageFinishedCallback: (url: String) -> Unit,
    private val onAdBlocked: () -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
            return false
        }
        // Handle external protocols (tel, mailto, etc.)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null

        if (isAdBlockEnabled() && AdBlocker.isAd(url)) {
            onAdBlocked()
            return AdBlocker.createEmptyResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun onLoadResource(view: WebView?, url: String?) {
        super.onLoadResource(view, url)
        if (!url.isNullOrBlank()) {
            mediaSniffer.addFromNetwork(url, view?.url.orEmpty())
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStartedCallback(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { onPageFinishedCallback(it) }

        // Inject ad blocker CSS
        if (isAdBlockEnabled()) {
            view?.evaluateJavascript(AdBlocker.CSS_AD_BLOCK_SCRIPT, null)
        }

        // Apply dark mode CSS if enabled
        if (isDarkMode()) {
            val darkCss = """
                (function() {
                    if (document.getElementById('swift_dark_style')) return;
                    var style = document.createElement('style');
                    style.id = 'swift_dark_style';
                    style.type = 'text/css';
                    style.innerHTML = `
                        html { filter: invert(90%) hue-rotate(180deg) !important; background-color: #121212 !important; }
                        img, video, iframe, canvas, svg { filter: invert(100%) hue-rotate(180deg) !important; }
                    `;
                    (document.head || document.documentElement).appendChild(style);
                })();
            """.trimIndent()
            view?.evaluateJavascript(darkCss, null)
        }

        // Sniff DOM media
        view?.evaluateJavascript(mediaSniffer.domSnifferScript, null)
    }
}

class BrowserWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onTitleReceivedCallback: (String) -> Unit,
    private val onFaviconReceivedCallback: (Bitmap?) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleReceivedCallback(it) }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        onFaviconReceivedCallback(icon)
    }
}
