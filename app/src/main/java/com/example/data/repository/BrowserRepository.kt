package com.example.data.repository

import com.example.data.local.BookmarkDao
import com.example.data.local.BookmarkEntity
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryDao
import com.example.data.local.HistoryEntity
import com.example.data.model.Bookmark
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BrowserRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val downloadDao: DownloadDao
) {
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks().map { list ->
        list.map { it.toBookmark() }
    }

    val history: Flow<List<HistoryItem>> = historyDao.getAllHistory().map { list ->
        list.map { it.toHistoryItem() }
    }

    val downloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads().map { list ->
        list.map { it.toDownloadItem() }
    }

    fun searchHistory(query: String): Flow<List<HistoryItem>> =
        historyDao.searchHistory(query).map { list ->
            list.map { it.toHistoryItem() }
        }

    suspend fun isBookmarked(url: String): Boolean {
        return bookmarkDao.getBookmarkByUrl(url) != null
    }

    suspend fun toggleBookmark(title: String, url: String, favicon: String? = null): Boolean {
        val existing = bookmarkDao.getBookmarkByUrl(url)
        return if (existing != null) {
            bookmarkDao.deleteBookmark(existing)
            false
        } else {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    title = title.ifBlank { url },
                    url = url,
                    favicon = favicon
                )
            )
            true
        }
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(BookmarkEntity.fromBookmark(bookmark))
    }

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val existing = historyDao.getHistoryByUrl(url)
        if (existing != null) {
            historyDao.updateHistory(
                existing.copy(
                    title = title.ifBlank { existing.title },
                    timestamp = System.currentTimeMillis(),
                    visitCount = existing.visitCount + 1
                )
            )
        } else {
            historyDao.insertHistory(
                HistoryEntity(
                    title = title.ifBlank { url },
                    url = url,
                    timestamp = System.currentTimeMillis(),
                    visitCount = 1
                )
            )
        }
    }

    suspend fun deleteHistory(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearHistory() {
        historyDao.clearAll()
    }

    suspend fun saveDownload(item: DownloadItem) {
        downloadDao.insertDownload(DownloadEntity.fromDownloadItem(item))
    }

    suspend fun updateDownload(item: DownloadItem) {
        downloadDao.updateDownload(DownloadEntity.fromDownloadItem(item))
    }

    suspend fun deleteDownload(item: DownloadItem) {
        downloadDao.deleteById(item.id)
    }

    suspend fun clearDownloads() {
        downloadDao.clearAll()
    }
}
