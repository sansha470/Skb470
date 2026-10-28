package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Bookmark
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.HistoryItem

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val favicon: String? = null
) {
    fun toBookmark(): Bookmark = Bookmark(
        id = id,
        title = title,
        url = url,
        timestamp = timestamp,
        favicon = favicon
    )

    companion object {
        fun fromBookmark(bookmark: Bookmark): BookmarkEntity = BookmarkEntity(
            id = bookmark.id,
            title = bookmark.title,
            url = bookmark.url,
            timestamp = bookmark.timestamp,
            favicon = bookmark.favicon
        )
    }
}

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
) {
    fun toHistoryItem(): HistoryItem = HistoryItem(
        id = id,
        title = title,
        url = url,
        timestamp = timestamp,
        visitCount = visitCount
    )

    companion object {
        fun fromHistoryItem(item: HistoryItem): HistoryEntity = HistoryEntity(
            id = item.id,
            title = item.title,
            url = item.url,
            timestamp = item.timestamp,
            visitCount = item.visitCount
        )
    }
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val url: String,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val status: String = DownloadStatus.DOWNLOADING.name,
    val progress: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val speed: String = ""
) {
    fun toDownloadItem(): DownloadItem = DownloadItem(
        id = id,
        fileName = fileName,
        url = url,
        filePath = filePath,
        fileSize = fileSize,
        mimeType = mimeType,
        status = try {
            DownloadStatus.valueOf(status)
        } catch (_: Exception) {
            DownloadStatus.COMPLETED
        },
        progress = progress,
        timestamp = timestamp,
        speed = speed
    )

    companion object {
        fun fromDownloadItem(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            fileName = item.fileName,
            url = item.url,
            filePath = item.filePath,
            fileSize = item.fileSize,
            mimeType = item.mimeType,
            status = item.status.name,
            progress = item.progress,
            timestamp = item.timestamp,
            speed = item.speed
        )
    }
}
