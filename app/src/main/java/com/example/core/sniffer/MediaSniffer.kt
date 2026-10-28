package com.example.core.sniffer

import android.webkit.JavascriptInterface
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import org.json.JSONArray
import org.json.JSONObject

class MediaSniffer(
    private val onMediaListUpdated: (List<MediaItem>) -> Unit
) {
    private val mediaMap = LinkedHashMap<String, MediaItem>()

    fun reset() {
        synchronized(mediaMap) {
            mediaMap.clear()
        }
        onMediaListUpdated(emptyList())
    }

    fun addFromNetwork(url: String, sourceUrl: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank() || cleanUrl.startsWith("data:") || cleanUrl.startsWith("blob:")) return
        if (isIgnoredUrl(cleanUrl)) return

        val type = detectMediaType(cleanUrl) ?: return
        val fileName = getFileNameFromUrl(cleanUrl)

        synchronized(mediaMap) {
            if (!mediaMap.containsKey(cleanUrl)) {
                val item = MediaItem(
                    url = cleanUrl,
                    type = type,
                    title = fileName,
                    thumbnail = if (type == MediaType.PHOTO) cleanUrl else null,
                    sourcePageUrl = sourceUrl
                )
                mediaMap[cleanUrl] = item
                onMediaListUpdated(mediaMap.values.toList())
            }
        }
    }

    fun addSingleMedia(item: MediaItem) {
        if (item.url.isBlank() || isIgnoredUrl(item.url)) return
        synchronized(mediaMap) {
            mediaMap[item.url] = item
            onMediaListUpdated(mediaMap.values.toList())
        }
    }

    fun getCurrentList(): List<MediaItem> {
        synchronized(mediaMap) {
            return mediaMap.values.toList()
        }
    }

    private fun isIgnoredUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("favicon") ||
                lower.contains("google-analytics") ||
                lower.contains("pixel") ||
                lower.contains("tracker") ||
                lower.contains("adservice") ||
                lower.contains("beacon") ||
                lower.contains(".js") ||
                lower.contains(".css")
    }

    private fun detectMediaType(url: String): MediaType? {
        val lower = url.lowercase().substringBefore("?")
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".m3u8") ||
                    lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
                    lower.contains(".mp4?") || lower.contains(".m3u8?") || lower.contains("/video/") -> MediaType.VIDEO

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                    lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".svg") -> MediaType.PHOTO

            lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".m4a") ||
                    lower.endsWith(".wav") || lower.endsWith(".aac") -> MediaType.AUDIO

            else -> null
        }
    }

    private fun getFileNameFromUrl(url: String): String {
        return try {
            val path = url.substringBefore("?").substringBefore("#")
            val name = path.substringAfterLast("/")
            if (name.isNotBlank() && name.length < 50) name else "media_file"
        } catch (_: Exception) {
            "media_file"
        }
    }

    val domSnifferScript: String = """
        (function() {
            var results = [];
            var seen = {};

            function addMedia(url, type, title, thumb, dimensions) {
                if (!url || seen[url] || url.startsWith('blob:') || url.startsWith('data:')) return;
                seen[url] = true;
                results.push({
                    url: url,
                    type: type,
                    title: title || 'Media File',
                    thumbnail: thumb,
                    dimensions: dimensions
                });
            }

            // 1. Meta tags (og:image, og:video, twitter:image)
            var metaImages = document.querySelectorAll('meta[property="og:image"], meta[name="twitter:image"], meta[property="og:image:url"]');
            metaImages.forEach(function(m) {
                if (m.content) addMedia(m.content, 'PHOTO', 'Page Featured Image', m.content, null);
            });
            var metaVideos = document.querySelectorAll('meta[property="og:video"], meta[name="twitter:player:stream"], meta[property="og:video:url"]');
            metaVideos.forEach(function(m) {
                if (m.content) addMedia(m.content, 'VIDEO', 'Page Featured Video', null, null);
            });

            // 2. Videos (<video>, <source>, video links)
            var videoEls = document.querySelectorAll('video, video source, a[href*=".mp4"], a[href*=".webm"], a[href*=".m3u8"], a[href*=".mov"]');
            videoEls.forEach(function(el) {
                var src = el.src || el.currentSrc || el.href;
                if (src) {
                    var poster = el.poster || null;
                    var dim = (el.videoWidth && el.videoHeight) ? (el.videoWidth + 'x' + el.videoHeight) : null;
                    addMedia(src, 'VIDEO', el.title || el.alt || 'Video File', poster, dim);
                }
            });

            // 3. Photos (<img>, <picture source>, srcset, <a> image links)
            var imgEls = document.querySelectorAll('img, picture source, a[href*=".jpg"], a[href*=".png"], a[href*=".webp"], a[href*=".gif"]');
            imgEls.forEach(function(img) {
                var src = img.src || img.currentSrc || img.href;
                if (src) {
                    var width = img.naturalWidth || img.width || 0;
                    var height = img.naturalHeight || img.height || 0;
                    var dim = (width > 0 && height > 0) ? (width + 'x' + height) : null;
                    addMedia(src, 'PHOTO', img.alt || img.title || 'Photo', src, dim);
                }
                // Handle srcset
                if (img.srcset) {
                    var parts = img.srcset.split(',');
                    parts.forEach(function(p) {
                        var u = p.trim().split(' ')[0];
                        if (u) addMedia(u, 'PHOTO', 'High-Res Photo', u, null);
                    });
                }
            });

            // 4. CSS Background images
            var allElements = document.querySelectorAll('div, section, header, banner, a');
            allElements.forEach(function(el) {
                var bg = window.getComputedStyle(el).backgroundImage;
                if (bg && bg !== 'none' && bg.startsWith('url(')) {
                    var cleanBg = bg.replace(/^url\(["']?/, '').replace(/["']?\)$/, '');
                    if (cleanBg && !cleanBg.startsWith('data:')) {
                        addMedia(cleanBg, 'PHOTO', 'Background Image', cleanBg, null);
                    }
                }
            });

            // 5. Audio (<audio>, <source>, audio links)
            var audioEls = document.querySelectorAll('audio, audio source, a[href*=".mp3"], a[href*=".wav"], a[href*=".ogg"]');
            audioEls.forEach(function(el) {
                var src = el.src || el.currentSrc || el.href;
                if (src) {
                    addMedia(src, 'AUDIO', el.title || 'Audio Track', null, null);
                }
            });

            if (results.length > 0 && window.AndroidMediaSniffer) {
                window.AndroidMediaSniffer.onMediaFound(JSON.stringify(results));
            }
        })();
    """

    inner class MediaSnifferBridge {
        @JavascriptInterface
        fun onMediaFound(jsonString: String) {
            try {
                val array = JSONArray(jsonString)
                val newItems = mutableListOf<MediaItem>()
                for (i in 0 until array.length()) {
                    val obj: JSONObject = array.getJSONObject(i)
                    val url = obj.optString("url", "")
                    if (url.isBlank() || isIgnoredUrl(url)) continue

                    val typeStr = obj.optString("type", "PHOTO")
                    val type = try {
                        MediaType.valueOf(typeStr)
                    } catch (_: Exception) {
                        MediaType.PHOTO
                    }

                    val title = obj.optString("title", "").ifBlank { getFileNameFromUrl(url) }
                    val thumbnail = obj.optString("thumbnail", null).takeIf { !it.isNullOrBlank() }
                    val dimensions = obj.optString("dimensions", null).takeIf { !it.isNullOrBlank() }

                    newItems.add(
                        MediaItem(
                            url = url,
                            type = type,
                            title = title,
                            thumbnail = thumbnail ?: if (type == MediaType.PHOTO) url else null,
                            dimensions = dimensions
                        )
                    )
                }

                synchronized(mediaMap) {
                    for (item in newItems) {
                        if (!mediaMap.containsKey(item.url)) {
                            mediaMap[item.url] = item
                        }
                    }
                    onMediaListUpdated(mediaMap.values.toList())
                }
            } catch (_: Exception) {
                // Ignore json parsing failures
            }
        }
    }
}
