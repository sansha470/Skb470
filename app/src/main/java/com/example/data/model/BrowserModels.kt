package com.example.data.model

enum class MediaType {
    VIDEO,
    PHOTO,
    AUDIO
}

data class MediaItem(
    val url: String,
    val type: MediaType,
    val title: String = "",
    val thumbnail: String? = null,
    val dimensions: String? = null,
    val fileSizeFormatted: String? = null,
    val sourcePageUrl: String = ""
)

data class Bookmark(
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val favicon: String? = null
)

data class HistoryItem(
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadItem(
    val id: String,
    val fileName: String,
    val url: String,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val progress: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: String = ""
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class SearchEngine(val displayName: String, val searchUrl: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search=")
}

data class SpeedDialItem(
    val title: String,
    val url: String,
    val iconEmoji: String,
    val isDefault: Boolean = true
)

data class BrowserTab(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isIncognito: Boolean = false,
    val grabbedMedia: List<MediaItem> = emptyList(),
    val blockedAdsCount: Int = 0
)
