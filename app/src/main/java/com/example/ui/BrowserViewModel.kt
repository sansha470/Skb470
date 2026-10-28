package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.download.BrowserDownloadManager
import com.example.data.local.BrowserDatabase
import com.example.data.model.Bookmark
import com.example.data.model.BrowserTab
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryItem
import com.example.data.model.MediaItem
import com.example.data.model.SearchEngine
import com.example.data.model.SpeedDialItem
import com.example.data.model.ThemeMode
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    private val database = BrowserDatabase.getDatabase(application)
    val repository = BrowserRepository(
        database.bookmarkDao(),
        database.historyDao(),
        database.downloadDao()
    )

    val downloadManager = BrowserDownloadManager(application, repository, viewModelScope)

    // Tabs
    private val _tabs = MutableStateFlow<List<BrowserTab>>(
        listOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "New Tab",
                url = ""
            )
        )
    )
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    // Settings
    private val _isAdBlockEnabled = MutableStateFlow(prefs.getBoolean("ad_block_enabled", true))
    val isAdBlockEnabled: StateFlow<Boolean> = _isAdBlockEnabled.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isWebViewDarkMode = MutableStateFlow(prefs.getBoolean("webview_dark_mode", false))
    val isWebViewDarkMode: StateFlow<Boolean> = _isWebViewDarkMode.asStateFlow()

    private val _searchEngine = MutableStateFlow(
        SearchEngine.valueOf(prefs.getString("search_engine", SearchEngine.GOOGLE.name) ?: SearchEngine.GOOGLE.name)
    )
    val searchEngine: StateFlow<SearchEngine> = _searchEngine.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _isIncognitoMode = MutableStateFlow(false)
    val isIncognitoMode: StateFlow<Boolean> = _isIncognitoMode.asStateFlow()

    // Bookmarks and History
    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val history: StateFlow<List<HistoryItem>> = repository.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloads: StateFlow<List<DownloadItem>> = repository.downloads.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Current page bookmarked state
    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    // URL input state
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    // Total blocked ads counter
    private val _totalBlockedAds = MutableStateFlow(prefs.getInt("total_blocked_ads", 42))
    val totalBlockedAds: StateFlow<Int> = _totalBlockedAds.asStateFlow()

    // Speed dial list
    private val _speedDials = MutableStateFlow(
        listOf(
            SpeedDialItem("Google", "https://www.google.com", "🔍"),
            SpeedDialItem("YouTube", "https://m.youtube.com", "▶️"),
            SpeedDialItem("Wikipedia", "https://en.wikipedia.org", "📚"),
            SpeedDialItem("Reddit", "https://www.reddit.com", "💬"),
            SpeedDialItem("GitHub", "https://github.com", "🐙"),
            SpeedDialItem("BBC News", "https://www.bbc.com/news", "📰"),
            SpeedDialItem("Yahoo", "https://www.yahoo.com", "🟣"),
            SpeedDialItem("Twitter/X", "https://x.com", "🐦")
        )
    )
    val speedDials: StateFlow<List<SpeedDialItem>> = _speedDials.asStateFlow()

    // Navigation trigger for WebView
    private val _navigationAction = MutableStateFlow<BrowserNavAction?>(null)
    val navigationAction: StateFlow<BrowserNavAction?> = _navigationAction.asStateFlow()

    fun clearNavigationAction() {
        _navigationAction.value = null
    }

    // Active tab accessor
    val activeTab: BrowserTab
        get() = _tabs.value.find { it.id == _activeTabId.value } ?: _tabs.value.first()

    fun updateUrlInput(text: String) {
        _urlInput.value = text
    }

    fun loadUrl(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> _searchEngine.value.searchUrl + UriEncoder.encode(trimmed)
        }

        updateActiveTab { it.copy(url = finalUrl, title = finalUrl, isLoading = true) }
        _urlInput.value = finalUrl
        _navigationAction.value = BrowserNavAction.LoadUrl(finalUrl)
        checkCurrentBookmark(finalUrl)
    }

    fun onPageStarted(url: String) {
        updateActiveTab { it.copy(url = url, isLoading = true, progress = 10) }
        _urlInput.value = url
        checkCurrentBookmark(url)
    }

    fun onPageFinished(url: String) {
        updateActiveTab { it.copy(url = url, isLoading = false, progress = 100) }
        _urlInput.value = url
        checkCurrentBookmark(url)

        if (!_isIncognitoMode.value && url.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.recordHistory(activeTab.title, url)
            }
        }
    }

    fun onProgressChanged(progress: Int) {
        updateActiveTab { it.copy(progress = progress, isLoading = progress < 100) }
    }

    fun onTitleReceived(title: String) {
        updateActiveTab { it.copy(title = title) }
        if (!_isIncognitoMode.value && activeTab.url.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.recordHistory(title, activeTab.url)
            }
        }
    }

    fun onAdBlocked() {
        updateActiveTab { it.copy(blockedAdsCount = it.blockedAdsCount + 1) }
        _totalBlockedAds.value += 1
        prefs.edit().putInt("total_blocked_ads", _totalBlockedAds.value).apply()
    }

    fun updateGrabbedMedia(mediaList: List<MediaItem>) {
        updateActiveTab { it.copy(grabbedMedia = mediaList) }
    }

    fun addManualMedia(item: MediaItem) {
        val current = activeTab.grabbedMedia.toMutableList()
        if (current.none { it.url == item.url }) {
            current.add(0, item)
            updateActiveTab { it.copy(grabbedMedia = current) }
        }
    }

    fun toggleBookmark() {
        val currentUrl = activeTab.url
        if (currentUrl.isBlank()) return
        val currentTitle = activeTab.title.ifBlank { currentUrl }

        viewModelScope.launch(Dispatchers.IO) {
            val bookmarked = repository.toggleBookmark(currentTitle, currentUrl)
            _isCurrentPageBookmarked.value = bookmarked
        }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookmark(bookmark)
            if (activeTab.url == bookmark.url) {
                _isCurrentPageBookmarked.value = false
            }
        }
    }

    private fun checkCurrentBookmark(url: String) {
        if (url.isBlank()) {
            _isCurrentPageBookmarked.value = false
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isCurrentPageBookmarked.value = repository.isBookmarked(url)
        }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHistory(item.id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun deleteDownload(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDownload(item)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearDownloads()
        }
    }

    // Media grabber direct download
    fun downloadMedia(item: MediaItem) {
        val ext = when (item.type) {
            com.example.data.model.MediaType.VIDEO -> "mp4"
            com.example.data.model.MediaType.PHOTO -> "jpg"
            com.example.data.model.MediaType.AUDIO -> "mp3"
        }
        val safeName = item.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(30)
        val fileName = if (safeName.contains(".")) safeName else "${safeName}_${System.currentTimeMillis()}.$ext"
        downloadManager.startDownload(
            url = item.url,
            customFileName = fileName
        )
    }

    // Tab Management
    fun createNewTab(url: String = "", isIncognito: Boolean = _isIncognitoMode.value) {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = if (url.isBlank()) "New Tab" else url,
            url = url,
            isIncognito = isIncognito
        )
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        _urlInput.value = url
        if (url.isNotBlank()) {
            _navigationAction.value = BrowserNavAction.LoadUrl(url)
        }
    }

    fun selectTab(id: String) {
        _activeTabId.value = id
        val tab = _tabs.value.find { it.id == id }
        _urlInput.value = tab?.url.orEmpty()
        checkCurrentBookmark(tab?.url.orEmpty())
    }

    fun closeTab(id: String) {
        val currentTabs = _tabs.value
        if (currentTabs.size <= 1) {
            // Replace with a clean new tab
            val freshTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "New Tab",
                url = "",
                isIncognito = _isIncognitoMode.value
            )
            _tabs.value = listOf(freshTab)
            _activeTabId.value = freshTab.id
            _urlInput.value = ""
            return
        }

        val remaining = currentTabs.filter { it.id != id }
        _tabs.value = remaining
        if (_activeTabId.value == id) {
            val nextTab = remaining.last()
            _activeTabId.value = nextTab.id
            _urlInput.value = nextTab.url
            checkCurrentBookmark(nextTab.url)
        }
    }

    // Settings actions
    fun toggleAdBlock() {
        val newValue = !_isAdBlockEnabled.value
        _isAdBlockEnabled.value = newValue
        prefs.edit().putBoolean("ad_block_enabled", newValue).apply()
        Toast.makeText(
            getApplication(),
            if (newValue) "AdBlocker ON" else "AdBlocker OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun toggleWebViewDarkMode() {
        val newValue = !_isWebViewDarkMode.value
        _isWebViewDarkMode.value = newValue
        prefs.edit().putBoolean("webview_dark_mode", newValue).apply()
        _navigationAction.value = BrowserNavAction.Reload
    }

    fun setSearchEngine(engine: SearchEngine) {
        _searchEngine.value = engine
        prefs.edit().putString("search_engine", engine.name).apply()
    }

    fun toggleDesktopMode() {
        val newValue = !_isDesktopMode.value
        _isDesktopMode.value = newValue
        _navigationAction.value = BrowserNavAction.SetDesktopMode(newValue)
    }

    fun toggleIncognito() {
        val newIncognito = !_isIncognitoMode.value
        _isIncognitoMode.value = newIncognito

        if (newIncognito) {
            // Clear current cookies/cache for privacy
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(getApplication(), "Secure Incognito Mode ON", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(getApplication(), "Incognito Mode OFF", Toast.LENGTH_SHORT).show()
        }

        // Open a new tab in this mode
        createNewTab(isIncognito = newIncognito)
    }

    fun addCustomSpeedDial(title: String, url: String) {
        val trimmedUrl = if (!url.startsWith("http")) "https://$url" else url
        val item = SpeedDialItem(
            title = title.ifBlank { "Site" },
            url = trimmedUrl,
            iconEmoji = "🌐",
            isDefault = false
        )
        _speedDials.value = _speedDials.value + item
    }

    // Navigation Controls
    fun goBack() {
        _navigationAction.value = BrowserNavAction.GoBack
    }

    fun goForward() {
        _navigationAction.value = BrowserNavAction.GoForward
    }

    fun reload() {
        _navigationAction.value = BrowserNavAction.Reload
    }

    fun goHome() {
        updateActiveTab { it.copy(url = "", title = "New Tab", grabbedMedia = emptyList()) }
        _urlInput.value = ""
        _isCurrentPageBookmarked.value = false
        _navigationAction.value = BrowserNavAction.LoadUrl("")
    }

    private fun updateActiveTab(transform: (BrowserTab) -> BrowserTab) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == _activeTabId.value) transform(tab) else tab
        }
    }
}

sealed class BrowserNavAction {
    data class LoadUrl(val url: String) : BrowserNavAction()
    object GoBack : BrowserNavAction()
    object GoForward : BrowserNavAction()
    object Reload : BrowserNavAction()
    data class SetDesktopMode(val enabled: Boolean) : BrowserNavAction()
}

object UriEncoder {
    fun encode(query: String): String {
        return try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (_: Exception) {
            query.replace(" ", "+")
        }
    }
}
