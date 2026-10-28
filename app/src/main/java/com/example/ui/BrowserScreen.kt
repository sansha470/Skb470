package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.adblocker.AdBlocker
import com.example.core.sniffer.MediaSniffer
import com.example.core.webview.BrowserWebChromeClient
import com.example.core.webview.BrowserWebViewClient
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.components.BookmarksSheet
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.ui.components.BrowserBottomBar
import com.example.ui.components.BrowserTopBar
import com.example.ui.components.DownloadsSheet
import com.example.ui.components.HistorySheet
import com.example.ui.components.MediaGrabberSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SpeedDialHome
import com.example.ui.components.TabSwitcherDialog
import com.example.ui.theme.GrabberOrange
import com.example.ui.theme.ShieldGreen

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State flows
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val isAdBlockEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
    val isIncognito by viewModel.isIncognitoMode.collectAsStateWithLifecycle()
    val isDesktopMode by viewModel.isDesktopMode.collectAsStateWithLifecycle()
    val isWebViewDarkMode by viewModel.isWebViewDarkMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val searchEngine by viewModel.searchEngine.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val totalBlockedAds by viewModel.totalBlockedAds.collectAsStateWithLifecycle()
    val speedDials by viewModel.speedDials.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val navAction by viewModel.navigationAction.collectAsStateWithLifecycle()

    val activeTab = viewModel.activeTab

    // Sheets & Dialogs visibility
    var showMediaGrabber by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTabSwitcher by remember { mutableStateOf(false) }
    var showAdBlockDialog by remember { mutableStateOf(false) }

    // Long press image context dialog
    var longPressedImageItem by remember { mutableStateOf<MediaItem?>(null) }

    // Bottom sheet states
    val mediaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarksSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val downloadsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Media sniffer instance
    val mediaSniffer = remember {
        MediaSniffer { list ->
            viewModel.updateGrabbedMedia(list)
        }
    }

    // Keep reference to webview
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Handle WebView navigation actions
    LaunchedEffect(navAction) {
        val action = navAction ?: return@LaunchedEffect
        when (action) {
            is BrowserNavAction.LoadUrl -> {
                if (action.url.isNotBlank()) {
                    mediaSniffer.reset()
                    webViewInstance?.loadUrl(action.url)
                }
            }
            BrowserNavAction.GoBack -> {
                if (webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                } else {
                    viewModel.goHome()
                }
            }
            BrowserNavAction.GoForward -> {
                if (webViewInstance?.canGoForward() == true) {
                    webViewInstance?.goForward()
                }
            }
            BrowserNavAction.Reload -> {
                webViewInstance?.reload()
            }
            is BrowserNavAction.SetDesktopMode -> {
                webViewInstance?.settings?.apply {
                    userAgentString = if (action.enabled) {
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        null
                    }
                }
                webViewInstance?.reload()
            }
        }
        viewModel.clearNavigationAction()
    }

    // Handle Hardware Back Button
    BackHandler {
        when {
            showMediaGrabber -> showMediaGrabber = false
            showBookmarks -> showBookmarks = false
            showHistory -> showHistory = false
            showDownloads -> showDownloads = false
            showSettings -> showSettings = false
            showTabSwitcher -> showTabSwitcher = false
            webViewInstance?.canGoBack() == true -> webViewInstance?.goBack()
            activeTab.url.isNotBlank() -> viewModel.goHome()
            else -> {
                // Exit app or close tab
                if (tabs.size > 1) {
                    viewModel.closeTab(activeTab.id)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BrowserTopBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                urlInput = urlInput,
                onUrlChange = { viewModel.updateUrlInput(it) },
                onSubmitUrl = { viewModel.loadUrl(it) },
                isLoading = activeTab.isLoading,
                progress = activeTab.progress,
                isIncognito = isIncognito,
                isAdBlockEnabled = isAdBlockEnabled,
                blockedAdsCount = activeTab.blockedAdsCount,
                isBookmarked = isBookmarked,
                tabCount = tabs.size,
                onToggleBookmark = { viewModel.toggleBookmark() },
                onReload = { viewModel.reload() },
                onOpenTabs = { showTabSwitcher = true },
                onOpenAdBlockDialog = { showAdBlockDialog = true }
            )
        },
        bottomBar = {
            BrowserBottomBar(
                canGoBack = webViewInstance?.canGoBack() == true || activeTab.url.isNotBlank(),
                canGoForward = webViewInstance?.canGoForward() == true,
                mediaCount = activeTab.grabbedMedia.size,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() },
                onHome = { viewModel.goHome() },
                onOpenMediaGrabber = { showMediaGrabber = true },
                onOpenDownloads = { showDownloads = true },
                onOpenMenu = { showSettings = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Android WebView
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            setSupportMultipleWindows(false)
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                        }

                        // Attach MediaSniffer JS Bridge
                        addJavascriptInterface(mediaSniffer.MediaSnifferBridge(), "AndroidMediaSniffer")

                        // Fast Download Listener
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                            viewModel.downloadManager.startDownload(
                                url = url,
                                contentDisposition = contentDisposition,
                                mimeType = mimetype
                            )
                        }

                        // Long Click Listener for Direct Image / Media Grab
                        setOnLongClickListener {
                            val result = hitTestResult
                            val type = result.type
                            val extra = result.extra
                            if (extra != null && (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                                longPressedImageItem = MediaItem(
                                    url = extra,
                                    type = MediaType.PHOTO,
                                    title = "Grabbed Photo",
                                    thumbnail = extra
                                )
                                true
                            } else {
                                false
                            }
                        }

                        webViewClient = BrowserWebViewClient(
                            context = ctx,
                            isAdBlockEnabled = { viewModel.isAdBlockEnabled.value },
                            isIncognito = { viewModel.isIncognitoMode.value },
                            isDarkMode = { viewModel.isWebViewDarkMode.value },
                            mediaSniffer = mediaSniffer,
                            onPageStartedCallback = { url ->
                                viewModel.onPageStarted(url)
                            },
                            onPageFinishedCallback = { url ->
                                viewModel.onPageFinished(url)
                            },
                            onAdBlocked = {
                                viewModel.onAdBlocked()
                            }
                        )

                        webChromeClient = BrowserWebChromeClient(
                            onProgressChangedCallback = { progress ->
                                viewModel.onProgressChanged(progress)
                            },
                            onTitleReceivedCallback = { title ->
                                viewModel.onTitleReceived(title)
                            },
                            onFaviconReceivedCallback = {}
                        )

                        webViewInstance = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView ->
                    webViewInstance = webView
                }
            )

            // Speed Dial Start Page shown when no active website
            AnimatedVisibility(
                visible = activeTab.url.isBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SpeedDialHome(
                    speedDials = speedDials,
                    recentBookmarks = bookmarks,
                    searchEngine = searchEngine,
                    isIncognito = isIncognito,
                    isAdBlockEnabled = isAdBlockEnabled,
                    totalBlockedAds = totalBlockedAds,
                    onOpenUrl = { viewModel.loadUrl(it) },
                    onAddSpeedDial = { title, url -> viewModel.addCustomSpeedDial(title, url) },
                    onToggleIncognito = { viewModel.toggleIncognito() },
                    onToggleAdBlock = { viewModel.toggleAdBlock() },
                    onOpenBookmarks = { showBookmarks = true },
                    onOpenHistory = { showHistory = true },
                    onOpenDownloads = { showDownloads = true }
                )
            }
        }
    }

    // Media Grabber Modal Sheet
    if (showMediaGrabber) {
        MediaGrabberSheet(
            mediaList = activeTab.grabbedMedia,
            onDownload = { item -> viewModel.downloadMedia(item) },
            onManualGrab = { item -> viewModel.addManualMedia(item) },
            sheetState = mediaSheetState,
            onDismiss = { showMediaGrabber = false }
        )
    }

    // Bookmarks Modal Sheet
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            onSelectBookmark = { url -> viewModel.loadUrl(url) },
            onDeleteBookmark = { bookmark -> viewModel.removeBookmark(bookmark) },
            sheetState = bookmarksSheetState,
            onDismiss = { showBookmarks = false }
        )
    }

    // History Modal Sheet
    if (showHistory) {
        HistorySheet(
            history = history,
            isIncognito = isIncognito,
            onSelectHistory = { url -> viewModel.loadUrl(url) },
            onDeleteHistory = { item -> viewModel.deleteHistory(item) },
            onClearAllHistory = { viewModel.clearAllHistory() },
            sheetState = historySheetState,
            onDismiss = { showHistory = false }
        )
    }

    // Downloads Modal Sheet
    if (showDownloads) {
        DownloadsSheet(
            downloads = downloads,
            onDeleteDownload = { item -> viewModel.deleteDownload(item) },
            onClearAll = { viewModel.clearAllDownloads() },
            sheetState = downloadsSheetState,
            onDismiss = { showDownloads = false }
        )
    }

    // Settings Modal Sheet
    if (showSettings) {
        SettingsSheet(
            themeMode = themeMode,
            isWebViewDarkMode = isWebViewDarkMode,
            isAdBlockEnabled = isAdBlockEnabled,
            totalBlockedAds = totalBlockedAds,
            isIncognito = isIncognito,
            isDesktopMode = isDesktopMode,
            searchEngine = searchEngine,
            onSetThemeMode = { viewModel.setThemeMode(it) },
            onToggleWebViewDarkMode = { viewModel.toggleWebViewDarkMode() },
            onToggleAdBlock = { viewModel.toggleAdBlock() },
            onToggleIncognito = { viewModel.toggleIncognito() },
            onToggleDesktopMode = { viewModel.toggleDesktopMode() },
            onSetSearchEngine = { viewModel.setSearchEngine(it) },
            sheetState = settingsSheetState,
            onDismiss = { showSettings = false }
        )
    }

    // Tab Switcher Dialog
    if (showTabSwitcher) {
        TabSwitcherDialog(
            tabs = tabs,
            activeTabId = activeTabId,
            onSelectTab = { id -> viewModel.selectTab(id) },
            onCloseTab = { id -> viewModel.closeTab(id) },
            onNewTab = { incognito -> viewModel.createNewTab(isIncognito = incognito) },
            onDismiss = { showTabSwitcher = false }
        )
    }

    // AdBlock Info Dialog
    if (showAdBlockDialog) {
        AlertDialog(
            onDismissRequest = { showAdBlockDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isAdBlockEnabled) ShieldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("AdBlocker Status")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isAdBlockEnabled) "AdBlocker is actively filtering advertisements and trackers." else "AdBlocker is currently turned off.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Ads blocked on this page: ${activeTab.blockedAdsCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Total ads blocked so far: $totalBlockedAds",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleAdBlock()
                        showAdBlockDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAdBlockEnabled) MaterialTheme.colorScheme.error else ShieldGreen
                    )
                ) {
                    Text(if (isAdBlockEnabled) "Turn OFF" else "Turn ON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdBlockDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Long press photo grab dialog
    longPressedImageItem?.let { item ->
        AlertDialog(
            onDismissRequest = { longPressedImageItem = null },
            title = { Text("Photo Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Would you like to download this image directly?", fontSize = 13.sp)
                    Text(
                        text = item.url,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.downloadMedia(item)
                        longPressedImageItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GrabberOrange)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Image URL", item.url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Image URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        longPressedImageItem = null
                    }
                ) {
                    Text("Copy Link")
                }
            }
        )
    }
}
