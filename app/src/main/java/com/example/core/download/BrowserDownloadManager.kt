package com.example.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.repository.BrowserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class BrowserDownloadManager(
    private val context: Context,
    private val repository: BrowserRepository,
    private val scope: CoroutineScope
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun startDownload(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        customFileName: String? = null
    ): String {
        val downloadId = UUID.randomUUID().toString()

        var fileName = customFileName?.trim().orEmpty()
        if (fileName.isBlank()) {
            fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        }
        if (fileName.isBlank() || fileName == "downloadfile") {
            val guessedExt = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "bin"
            fileName = "download_${System.currentTimeMillis()}.$guessedExt"
        }

        val cleanMime = mimeType ?: getMimeTypeFromUrl(url)

        try {
            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("Sant Browser Download")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                cleanMime?.let { setMimeType(it) }
            }

            val systemId = downloadManager.enqueue(request)

            val downloadItem = DownloadItem(
                id = downloadId,
                fileName = fileName,
                url = url,
                filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName,
                fileSize = 0L,
                mimeType = cleanMime ?: "application/octet-stream",
                status = DownloadStatus.DOWNLOADING,
                progress = 0,
                timestamp = System.currentTimeMillis(),
                speed = "High speed"
            )

            scope.launch(Dispatchers.IO) {
                repository.saveDownload(downloadItem)
            }

            Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }

        return downloadId
    }

    private fun getMimeTypeFromUrl(url: String): String? {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension.isNotBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        } else {
            null
        }
    }
}
