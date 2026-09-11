package com.jiqu.lite.data

import com.jiqu.lite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MediaDownloadOption(
    val quality: String,
    val width: Int?,
    val height: Int?,
    val bitRate: Long?,
    val sizeBytes: Long?,
    val downloadUrl: String,
    val fileExtension: String
) {
    val resolution: String
        get() = if (quality.contains("original", ignoreCase = true) ||
            quality.contains("origin", ignoreCase = true) ||
            quality.contains("原画") || quality.contains("原始")
        ) {
            "原画"
        } else if (width != null && height != null) {
            "$width × $height"
        } else {
            quality
        }
}

data class ParsedMedia(
    val platform: String,
    val title: String,
    val resolution: String,
    val bitRate: Long?,
    val mediaType: String,
    val fileExtension: String,
    val sizeBytes: Long?,
    val downloadUrl: String,
    val coverUrl: String?,
    val audioUrl: String?,
    val downloadOptions: List<MediaDownloadOption>,
    val assets: List<MediaAsset> = emptyList(),
    val durationMs: Long? = null
)

data class MediaAsset(
    val option: MediaDownloadOption,
    val thumbnailUrl: String?,
    val isLive: Boolean = false
)

enum class ParsePhase {
    CONNECTING,
    EXTRACTING
}

private const val BUGPK_API_BASE = "https://api.bugpk.com/api/"
private const val DOUYIN_MEDIA_ENDPOINT = "https://api-new.ifphp.com/api/dyjx"
private const val WECHAT_CHANNELS_MEDIA_ENDPOINT = "https://api-new.ifphp.com/api/wxsph"
private const val KUAISHOU_MEDIA_ENDPOINT = "https://api-new.ifphp.com/api/ksjx"
private const val BILIBILI_MEDIA_ENDPOINT = "https://api-new.ifphp.com/api/bilibili"
private const val PARSE_CACHE_DURATION_MS = 5 * 60 * 1_000L
private const val DOUYIN_HIGH_QUALITY_PLAY_ENDPOINT = "https://www.douyin.com/aweme/v1/play/"
private val parseCache = mutableMapOf<String, CachedParse>()
private val parseInFlight = mutableMapOf<String, Deferred<Result<ParsedMedia>>>()
private val parseInFlightMutex = Mutex()
private val parseRequestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class CachedParse(val media: ParsedMedia, val cachedAt: Long)

internal fun isDouyinUrl(sourceUrl: String): Boolean {
    val host = runCatching { URL(sourceUrl).host.lowercase() }.getOrDefault("")
    return host != "qishui.douyin.com" &&
        (host == "douyin.com" || host.endsWith(".douyin.com") ||
        host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")
        )
}

internal fun normalizeMediaSourceUrl(sourceUrl: String): String = sourceUrl
    .trim()
    // Links copied from Markdown or escaped text can contain `\\_` in the path.
    .replace("\\", "")

private const val DOUYIN_SHORT_HOST = "v.douyin.com"
private val douyinVideoPathPattern = Regex("""/(?:share/)?video/(\d+)""", RegexOption.IGNORE_CASE)

internal fun isWechatChannelsUrl(sourceUrl: String): Boolean {
    val host = runCatching { URL(sourceUrl).host.lowercase() }.getOrDefault("")
    return host == "channels.weixin.qq.com" || host.endsWith(".channels.weixin.qq.com") ||
        host == "weixin.qq.com" || host.endsWith(".weixin.qq.com") ||
        host == "v.weixin.qq.com" || host.endsWith(".v.weixin.qq.com")
}

internal fun isKuaishouUrl(sourceUrl: String): Boolean {
    val host = runCatching { URL(sourceUrl).host.lowercase() }.getOrDefault("")
    return host == "kuaishou.com" || host.endsWith(".kuaishou.com") ||
        host == "kwai.com" || host.endsWith(".kwai.com")
}
internal fun isBilibiliUrl(sourceUrl: String): Boolean =
    hasHost(sourceUrl, "bilibili.com", "b23.tv")

private fun hasHost(sourceUrl: String, vararg hosts: String): Boolean {
    val host = runCatching { URL(sourceUrl).host.lowercase() }.getOrDefault("")
    return hosts.any { host == it || host.endsWith(".$it") }
}

internal fun isDoubaoUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "doubao.com")
internal fun isJimengUrl(sourceUrl: String): Boolean =
    hasHost(sourceUrl, "jimeng.jianying.com", "dreamina.com")
internal fun isPipigxUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "pipigx.com")
internal fun isPipixiaUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "pipix.com", "pipixia.com")
internal fun isXiaohongshuUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "xiaohongshu.com", "xhslink.com")
internal fun isZuiyouUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "izuiyou.com", "xiaochuankeji.cn")
internal fun isToutiaoUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "toutiao.com", "toutiaovod.com")
private enum class MediaPlatform(val displayName: String, val requiresApiKey: Boolean = false) {
    DOUYIN("douyin", true),
    BILIBILI("哔哩哔哩", true),
    WECHAT_CHANNELS("微信视频号", true),
    KUAISHOU("快手", true),
    DOUBAO("豆包", true),
    JIMENG("即梦", true),
    PIPIGX("皮皮搞笑", true),
    PIPIXIA("皮皮虾"),
    XIAOHONGSHU("小红书"),
    ZUIYOU("最右"),
    TOUTIAO("今日头条")
}

private fun mediaPlatformForUrl(sourceUrl: String): MediaPlatform? = when {
    isDouyinUrl(sourceUrl) -> MediaPlatform.DOUYIN
    isBilibiliUrl(sourceUrl) -> MediaPlatform.BILIBILI
    isWechatChannelsUrl(sourceUrl) -> MediaPlatform.WECHAT_CHANNELS
    isKuaishouUrl(sourceUrl) -> MediaPlatform.KUAISHOU
    isDoubaoUrl(sourceUrl) -> MediaPlatform.DOUBAO
    isJimengUrl(sourceUrl) -> MediaPlatform.JIMENG
    isPipigxUrl(sourceUrl) -> MediaPlatform.PIPIGX
    isPipixiaUrl(sourceUrl) -> MediaPlatform.PIPIXIA
    isXiaohongshuUrl(sourceUrl) -> MediaPlatform.XIAOHONGSHU
    isZuiyouUrl(sourceUrl) -> MediaPlatform.ZUIYOU
    isToutiaoUrl(sourceUrl) -> MediaPlatform.TOUTIAO
    else -> null
}

internal fun isSupportedMediaUrl(sourceUrl: String): Boolean = mediaPlatformForUrl(sourceUrl) != null

suspend fun parseMediaUrl(
    sourceUrl: String,
    onPhase: suspend (ParsePhase) -> Unit = {}
): Result<ParsedMedia> = coroutineScope {
    val normalizedSourceUrl = normalizeMediaSourceUrl(sourceUrl)
    val request = parseInFlightMutex.withLock {
        parseInFlight[normalizedSourceUrl]
            ?: parseRequestScope.async { parseMediaUrlInternal(normalizedSourceUrl, onPhase) }
                .also { parseInFlight[normalizedSourceUrl] = it }
    }
    try {
        request.await()
    } finally {
        parseInFlightMutex.withLock {
            if (parseInFlight[normalizedSourceUrl] === request) parseInFlight.remove(normalizedSourceUrl)
        }
    }
}

private suspend fun parseMediaUrlInternal(
    normalizedSourceUrl: String,
    onPhase: suspend (ParsePhase) -> Unit
): Result<ParsedMedia> = withContext(Dispatchers.IO) {
    runCatching {
        require(normalizedSourceUrl.startsWith("http://") || normalizedSourceUrl.startsWith("https://")) { "请输入有效链接" }
        val platform = mediaPlatformForUrl(normalizedSourceUrl)
        require(platform != null) { "暂不支持该平台链接" }
        if (platform.requiresApiKey) {
            check(BuildConfig.DOUYIN_API_KEY.isNotBlank()) { "未配置解析密钥" }
        }
        synchronized(parseCache) {
            parseCache[normalizedSourceUrl]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt < PARSE_CACHE_DURATION_MS }
                ?.media
        }?.let { return@runCatching it }

        onPhase(ParsePhase.CONNECTING)
        val apiSourceUrl = if (platform == MediaPlatform.DOUYIN) {
            canonicalizeDouyinSourceUrl(normalizedSourceUrl)
        } else {
            normalizedSourceUrl
        }
        val apiSourceUrls = listOf(apiSourceUrl, normalizedSourceUrl).distinct()
        val candidateResults = coroutineScope {
            List(PARSE_SAMPLE_COUNT) {
                async(Dispatchers.IO) {
                    runCatching {
                        val root = requestParseResultWithRetry(apiSourceUrls, platform!!)
                        onPhase(ParsePhase.EXTRACTING)
                        val data = root.optJSONObject("data") ?: throw IllegalStateException("解析结果为空")
                        val durationMs = mediaDurationMs(data)
                        val defaultFormat = data.optString("format").ifBlank { "mp4" }
                        ParseCandidate(
                            root = root,
                            data = data,
                            options = buildDownloadOptions(data, defaultFormat, durationMs, platform!!)
                        )
                    }
                }
            }.awaitAll()
        }
        val candidates = candidateResults.mapNotNull { it.getOrNull() }
        if (candidates.isEmpty()) {
            throw candidateResults.mapNotNull { it.exceptionOrNull() }.lastOrNull()
                ?: IllegalStateException("解析服务暂时不可用")
        }
        // Resolver responses vary by platform. Preserve every supplied or
        // bitrate-estimated size, and only probe URLs whose size is still unknown.
        // The one-byte range request is shared by all platforms and fails open,
        // so an incompatible CDN cannot prevent the parse result from appearing.
        val sizedCandidates = candidates.map { candidate ->
            candidate.copy(options = enrichDownloadOptionSizes(candidate.options))
        }
        val bestCandidate = sizedCandidates.maxByOrNull { candidateQuality(it.options) }!!
        val root = bestCandidate.root
        val data = bestCandidate.data
        val durationMs = mediaDurationMs(data)
        val mergedOptions = mergeDownloadOptions(sizedCandidates.flatMap { it.options })
        val rawType = data.optString("type").lowercase()
        val largestExplicitVideo = mergedOptions
            .filter { resolutionRank(it) > 0 }
            .mapNotNull { it.sizeBytes }
            .maxOrNull()
        val options = if (rawType !in setOf("image", "images", "gallery", "photo") && largestExplicitVideo != null) {
            // Drop tiny anonymous URLs (usually cover/avatar assets) that some
            // resolvers expose as an unqualified 高清 candidate.
            mergedOptions.filterNot {
                it.quality.trim().equals("高清", ignoreCase = true) &&
                    it.sizeBytes != null && it.sizeBytes < largestExplicitVideo / 10
            }
        } else {
            mergedOptions
        }
        if (options.isEmpty()) throw IllegalStateException("未找到可下载媒体地址")
        val music = data.optJSONObject("music")
        val primaryOption = options.first()
        val highestResolutionOption = options.maxWithOrNull(
            compareBy<MediaDownloadOption> { resolutionRank(it) }
                .thenBy { pixelCount(it) }
                .thenBy { it.bitRate ?: 0L }
                .thenBy { it.sizeBytes ?: 0L }
                .thenBy { it.width ?: 0 }
                .thenBy { it.height ?: 0 }
        ) ?: primaryOption
        val highestBitRate = options.mapNotNull { it.bitRate }.maxOrNull()
        val largestSize = options.mapNotNull { it.sizeBytes }.maxOrNull()
        val assets = buildMediaAssets(data, options, primaryOption.fileExtension)
        ParsedMedia(
            platform = root.optString("platform")
                .ifBlank { data.optString("platform") }
                .ifBlank { platform!!.displayName },
            title = data.optString("title").ifBlank { "媒体内容" },
            resolution = highestResolutionOption.resolution
                .replace("original", "原画", ignoreCase = true)
                .ifBlank { "高清" },
            bitRate = highestBitRate,
            mediaType = when (rawType) {
                "live_photo", "livephoto" -> "实况"
                "image", "images", "gallery", "photo" -> "图片"
                "live" -> if (data.optJSONArray("live_photo")?.length()?.let { it > 0 } == true) "实况" else "视频"
                else -> "视频"
            },
            fileExtension = primaryOption.fileExtension,
            sizeBytes = largestSize,
            downloadUrl = primaryOption.downloadUrl,
            coverUrl = data.optString("cover").ifBlank { null },
            audioUrl = data.optString("music_url")
                .ifBlank { data.optString("audio_url") }
                .ifBlank { data.optString("audio") }
                .ifBlank { music?.optString("url").orEmpty() }
                .ifBlank { null },
            downloadOptions = options,
            assets = assets,
            durationMs = durationMs
        ).also { media ->
            synchronized(parseCache) {
                parseCache[normalizedSourceUrl] = CachedParse(media, System.currentTimeMillis())
            }
        }
    }
}

private fun buildMediaAssets(
    data: JSONObject,
    options: List<MediaDownloadOption>,
    defaultFormat: String
): List<MediaAsset> {
    val assets = mutableListOf<MediaAsset>()
    val seen = mutableSetOf<String>()
    val liveByImage = linkedMapOf<String, Pair<String, String?>>()
    data.optJSONArray("live_photo")?.let { items ->
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val video = item.optString("video").ifBlank { item.optString("url") }
            val image = item.optString("image").ifBlank { item.optString("image_url") }
            if (video.isNotBlank()) liveByImage[image] = video to image
        }
    }
    fun add(url: String, thumb: String?, live: Boolean, format: String = defaultFormat) {
        val normalized = if (url.startsWith("//")) "https:$url" else url
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
        if (!seen.add(normalized)) return
        val matched = options.firstOrNull { it.downloadUrl == normalized }
        val option = matched ?: MediaDownloadOption(
            quality = if (live) "实况" else "图片",
            width = null,
            height = null,
            bitRate = null,
            sizeBytes = null,
            downloadUrl = normalized,
            fileExtension = format
        )
        assets += MediaAsset(option, thumb?.takeIf { it.isNotBlank() } ?: normalized, live)
    }
    fun imageFormat(url: String): String = url.substringBefore('?').substringAfterLast('.', "jpg")
        .lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "avif") } ?: "jpg"
    data.optJSONArray("images")?.let { items ->
        for (i in 0 until items.length()) {
            when (val item = items.opt(i)) {
                is String -> {
                    val live = liveByImage[item]
                    if (live != null) add(live.first, item, true, "mp4") else add(item, item, false, imageFormat(item))
                }
                is JSONObject -> add(
                    item.optString("url").ifBlank { item.optString("image_url") },
                    item.optString("cover").ifBlank { item.optString("thumbnail") },
                    false,
                    item.optString("format").ifBlank { imageFormat(item.optString("url").ifBlank { item.optString("image_url") }) }
                )
            }
        }
    }
    data.optJSONArray("image_list")?.let { items ->
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val url = item.optString("url").ifBlank { item.optString("image_url") }
            add(url, item.optString("cover"), false, imageFormat(url))
        }
    }
    liveByImage.values.forEach { (video, image) -> add(video, image, true, "mp4") }
    return assets
}

private const val PARSE_SAMPLE_COUNT = 1
private const val PARSE_ATTEMPT_COUNT = 3

private data class ParseCandidate(
    val root: JSONObject,
    val data: JSONObject,
    val options: List<MediaDownloadOption>
)

private fun candidateQuality(options: List<MediaDownloadOption>): Long = options.maxOfOrNull {
    resolutionRank(it) * 1_000_000_000L + (it.bitRate ?: 0) * 1_000L + (it.sizeBytes ?: 0)
} ?: 0L

private fun mergeDownloadOptions(options: List<MediaDownloadOption>): List<MediaDownloadOption> =
    options.groupBy(::qualityBucket).values.map { variants ->
        variants.maxWithOrNull(
            compareBy<MediaDownloadOption> { pixelCount(it) }
                .thenBy { it.bitRate ?: 0L }
                .thenBy { it.sizeBytes ?: 0L }
                .thenBy { it.width ?: 0 }
                .thenBy { it.height ?: 0 }
        ) ?: variants.first()
    }.sortedWith(
        compareByDescending<MediaDownloadOption> { resolutionRank(it) }
            .thenByDescending { it.bitRate ?: 0L }
            .thenByDescending { it.sizeBytes ?: 0L }
    )

private fun qualityBucket(option: MediaDownloadOption): String {
    val normalized = option.quality.trim().lowercase()
    if (normalized.contains("original") || normalized.contains("origin") ||
        normalized.contains("原画") || normalized.contains("原始") || normalized == "raw"
    ) {
        return "original"
    }
    val qualityHeight = qualityHeight(normalized)
    if (qualityHeight > 0) return "height:$qualityHeight"
    val height = option.height ?: 0
    if (height > 0) return "height:$height"
    val width = option.width ?: 0
    if (width > 0) return "dimensions:${width}x$height"
    return "label:${normalized.ifBlank { "unknown" }}"
}

private suspend fun enrichDownloadOptionSizes(
    options: List<MediaDownloadOption>
): List<MediaDownloadOption> = coroutineScope {
    options.map { option ->
        async(Dispatchers.IO) {
            if (option.sizeBytes != null) {
                option
            } else {
                option.copy(sizeBytes = fetchRemoteFileSize(option.downloadUrl))
            }
        }
    }.awaitAll()
}

private fun fetchRemoteFileSize(url: String): Long? {
    fun request(method: String, range: String? = null): Long? {
        val remoteUrl = URL(url)
        val connection = (remoteUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
            if (remoteUrl.host.equals("bilivideo.com", ignoreCase = true) ||
                remoteUrl.host.endsWith(".bilivideo.com", ignoreCase = true)
            ) {
                setRequestProperty("Referer", "https://www.bilibili.com/")
            }
            range?.let { setRequestProperty("Range", it) }
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) return null
            val rangeTotal = connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/')
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
            if (responseCode == HttpURLConnection.HTTP_PARTIAL && rangeTotal == null) return null
            rangeTotal ?: connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    return request("GET", "bytes=0-0") ?: request("HEAD")
}

private fun resolutionRank(option: MediaDownloadOption): Long {
    val normalizedQuality = option.quality.trim().lowercase()
    if (normalizedQuality.contains("original") || normalizedQuality.contains("origin") ||
        normalizedQuality.contains("原画") || normalizedQuality.contains("原始") ||
        normalizedQuality == "raw"
    ) {
        return 1_000_000_000L
    }
    val width = option.width ?: 0
    val height = option.height ?: qualityHeight(option.quality)
    return if (width > 0 && height > 0) width.toLong() * height else height.toLong() * height
}

private fun pixelCount(option: MediaDownloadOption): Long {
    val width = option.width ?: 0
    val height = option.height ?: 0
    return if (width > 0 && height > 0) width.toLong() * height else 0L
}

private fun qualityHeight(quality: String): Int {
    val normalized = quality.lowercase()
    val numericHeight = Regex("(\\d{3,4})p").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
    if (numericHeight != null) return numericHeight
    return when {
        "4k" in normalized || "uhd" in normalized -> 2160
        "original" in normalized || "origin" in normalized || "原画" in normalized -> 4320
        "fhd" in normalized -> 1080
        "hd" in normalized -> 720
        "sd" in normalized -> 480
        else -> 0
    }
}

private fun mediaDurationMs(data: JSONObject): Long? =
    sequenceOf(
        data.optJSONObject("extra")?.optLong("duration_ms") ?: 0L,
        data.optLong("duration_ms"),
        data.optLong("video_duration_ms")
    ).firstOrNull { it > 0 }
        ?: sequenceOf(
            data.optDouble("duration"),
            data.optDouble("video_duration"),
            data.optJSONObject("extra")?.optDouble("duration") ?: 0.0
        ).firstOrNull { it > 0.0 }?.let { (it * 1_000).toLong() }

private fun canonicalizeDouyinSourceUrl(sourceUrl: String): String {
    val parsedUrl = runCatching { URL(sourceUrl) }.getOrNull() ?: return sourceUrl
    douyinVideoPathPattern.find(parsedUrl.path)?.groupValues?.getOrNull(1)?.let { videoId ->
        return "https://www.douyin.com/video/$videoId"
    }
    if (!parsedUrl.host.equals(DOUYIN_SHORT_HOST, ignoreCase = true)) return sourceUrl

    val connection = (parsedUrl.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 8_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
    }
    return try {
        val location = connection.getHeaderField("Location") ?: return sourceUrl
        val redirectedUrl = URL(parsedUrl, location)
        val videoId = douyinVideoPathPattern.find(redirectedUrl.path)?.groupValues?.getOrNull(1)
            ?: return sourceUrl
        "https://www.douyin.com/video/$videoId"
    } catch (_: Exception) {
        sourceUrl
    } finally {
        connection.disconnect()
    }
}

private fun buildDownloadOptions(
    data: JSONObject,
    defaultFormat: String,
    durationMs: Long?,
    platform: MediaPlatform
): List<MediaDownloadOption> {
    val options = linkedMapOf<String, MediaDownloadOption>()

    fun addOption(
        url: String,
        quality: String = "",
        width: Int? = null,
        height: Int? = null,
        bitRate: Long? = null,
        sizeBytes: Long? = null,
        format: String = ""
    ) {
        if (url.isBlank()) return
        val normalizedUrl = if (url.startsWith("//")) "https:$url" else url
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) return
        val estimatedSize = if (sizeBytes == null && durationMs != null && bitRate != null) {
            bitRate * durationMs / 8_000
        } else {
            sizeBytes
        }
        val normalizedQuality = quality.ifBlank { "高清" }
            .replace("original", "原画", ignoreCase = true)
            .replace("origin", "原画", ignoreCase = true)
        val candidate = MediaDownloadOption(
            quality = normalizedQuality,
            width = width,
            height = height,
            bitRate = bitRate,
            sizeBytes = estimatedSize,
            downloadUrl = normalizedUrl,
            fileExtension = format.ifBlank { defaultFormat }
        )
        val existing = options[normalizedUrl]
        options[normalizedUrl] = if (existing == null) candidate else MediaDownloadOption(
            quality = existing.quality.takeUnless { it == "高清" } ?: candidate.quality,
            width = existing.width ?: candidate.width,
            height = existing.height ?: candidate.height,
            bitRate = maxOf(existing.bitRate ?: 0, candidate.bitRate ?: 0).takeIf { it > 0 },
            sizeBytes = maxOf(existing.sizeBytes ?: 0, candidate.sizeBytes ?: 0).takeIf { it > 0 },
            downloadUrl = normalizedUrl,
            fileExtension = existing.fileExtension.ifBlank { candidate.fileExtension }
        )
    }

    fun addJsonOption(item: JSONObject) {
        val acceptedQuality = item.optJSONArray("accept")?.optString(0).orEmpty()
        val url = item.optString("url")
            .ifBlank { item.optString("download_url") }
            .ifBlank { item.optString("video_url") }
        if (url.isNotBlank()) {
            addOption(
                url = url,
                quality = item.optString("quality")
                    .ifBlank { item.optString("label") }
                    .ifBlank { acceptedQuality },
                width = item.optInt("width").takeIf { it > 0 },
                height = item.optInt("height").takeIf { it > 0 },
                bitRate = item.optLong("bit_rate").takeIf { it > 0 }
                    ?: item.optLong("bitrate").takeIf { it > 0 },
                sizeBytes = item.optLong("size").takeIf { it > 0 },
                format = item.optString("format")
            )
            return
        }
        val playAddress = item.optJSONObject("play_addr") ?: item.optJSONObject("playAddress")
        val urlList = playAddress?.optJSONArray("url_list") ?: item.optJSONArray("url_list")
        if (urlList != null) {
            for (index in 0 until urlList.length()) {
                addOption(
                    url = urlList.optString(index),
                    quality = item.optString("quality")
                        .ifBlank { item.optString("label") }
                        .ifBlank { acceptedQuality },
                    width = item.optInt("width").takeIf { it > 0 },
                    height = item.optInt("height").takeIf { it > 0 },
                    bitRate = item.optLong("bit_rate").takeIf { it > 0 }
                        ?: item.optLong("bitrate").takeIf { it > 0 },
                    sizeBytes = item.optLong("size").takeIf { it > 0 },
                    format = item.optString("format")
                )
            }
        }
    }

    fun addJsonOrStringOption(item: Any?) {
        when (item) {
            is JSONObject -> addJsonOption(item)
            is String -> addOption(item)
        }
    }

    data.optJSONArray("video_backup")?.let { items ->
        for (index in 0 until items.length()) addJsonOrStringOption(items.opt(index))
    }
    data.optJSONArray("video_qualities")?.let { items ->
        for (index in 0 until items.length()) addJsonOrStringOption(items.opt(index))
    }
    data.optJSONArray("videos")?.let { items ->
        for (index in 0 until items.length()) addJsonOrStringOption(items.opt(index))
    }
    data.optJSONArray("quality_urls")?.let { items ->
        for (index in 0 until items.length()) addJsonOrStringOption(items.opt(index))
    }
    data.optJSONObject("quality_urls")?.let { qualityUrls ->
        qualityUrls.keys().forEach { quality -> addOption(qualityUrls.optString(quality), quality) }
    }
    addOption(
        url = data.optString("url"),
        // The API's primary URL is not necessarily the source/original stream.
        // Trust its explicit quality metadata instead of labelling every Douyin URL as 原画.
        quality = data.optString("quality")
            .ifBlank { data.optString("quality_label") }
            .ifBlank {
                val width = data.optInt("width")
                val height = data.optInt("height")
                if (width >= 2160 || height >= 2160) "原画" else "高清"
            },
        width = data.optInt("width").takeIf { it > 0 },
        height = data.optInt("height").takeIf { it > 0 },
        bitRate = data.optLong("bit_rate").takeIf { it > 0 }
            ?: data.optLong("bitrate").takeIf { it > 0 },
        sizeBytes = data.optLong("size").takeIf { it > 0 },
        format = data.optString("format")
    )

    // 快手实况返回的是 live_photo 数组，统一模型当前只支持一个主下载地址。
    if (options.isEmpty()) {
        data.optJSONArray("live_photo")?.optJSONObject(0)?.let { livePhoto ->
            addOption(
                url = livePhoto.optString("video"),
                quality = "实况",
                format = "mp4"
            )
        }
    }
    if (options.isEmpty()) {
        data.optJSONArray("images")?.optString(0)?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            addOption(url = imageUrl, quality = "图片", format = "webp")
        }
    }

    // Some platforms nest their highest-quality URLs under play/video objects
    // instead of exposing them in the top-level arrays. Walk every response so
    // those candidates participate in the same size/quality ranking.
    run {
        val rawType = data.optString("type").lowercase()
        val imageMedia = rawType in setOf("image", "images", "gallery", "photo")
        fun visit(value: Any?, key: String = "", parentKey: String = "") {
            when (value) {
                is JSONObject -> value.keys().forEach { child -> visit(value.opt(child), child, key) }
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i), key, parentKey)
                is String -> {
                    val normalized = if (value.startsWith("//")) "https:$value" else value
                    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
                    val lower = normalized.lowercase()
                    val keyPath = "${parentKey.lowercase()} ${key.lowercase()}"
                    val excludedImage = keyPath.contains("avatar") || keyPath.contains("cover") || keyPath.contains("author")
                    val image = !excludedImage &&
                        (keyPath.contains("image") ||
                            lower.substringBefore('?').substringAfterLast('.').let { it in setOf("jpg", "jpeg", "png", "webp", "gif") })
                    val video = !image && (keyPath.contains("video") || keyPath.contains("play") ||
                        keyPath.contains("download") ||
                        lower.contains(".mp4") || lower.contains("video"))
                    // Video posts must not expose covers/avatars as download choices.
                    // Image posts keep their image assets while still accepting video/live URLs.
                    if ((image && imageMedia) || video || (key.isBlank() && !image)) {
                        if (image) {
                            addOption(normalized, "图片", format = "jpg")
                        } else {
                            val original = keyPath.contains("original") || keyPath.contains("origin") ||
                                keyPath.contains("source") || keyPath.contains("best")
                            // Avoid adding anonymous nested URLs as a duplicate 高清 option
                            // when explicit quality candidates are already available.
                            if (original || options.isEmpty()) {
                                addOption(normalized, if (original) "原画" else "高清", format = "mp4")
                            }
                        }
                    }
                }
            }
        }
        visit(data)
    }

    return options.values.sortedWith(
        compareByDescending<MediaDownloadOption> { resolutionRank(it) }
            .thenByDescending { it.bitRate ?: 0 }
            .thenByDescending { it.sizeBytes ?: 0 }
    )
}

private suspend fun requestParseResultWithRetry(
    sourceUrls: List<String>,
    platform: MediaPlatform
): JSONObject {
    var lastFailure: Throwable? = null
    repeat(PARSE_ATTEMPT_COUNT) { attempt ->
        for (sourceUrl in sourceUrls) {
            try {
                return requestParseResult(sourceUrl, platform)
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                lastFailure = failure
            }
        }
        if (attempt + 1 < PARSE_ATTEMPT_COUNT) delay(350L * (attempt + 1))
    }
    throw lastFailure ?: IllegalStateException("${platform.displayName}解析服务暂时不可用")
}

private fun requestParseResult(sourceUrl: String, platform: MediaPlatform): JSONObject {
    val endpoint = when (platform) {
        MediaPlatform.DOUYIN -> DOUYIN_MEDIA_ENDPOINT
        MediaPlatform.BILIBILI -> BILIBILI_MEDIA_ENDPOINT
        MediaPlatform.WECHAT_CHANNELS -> WECHAT_CHANNELS_MEDIA_ENDPOINT
        MediaPlatform.KUAISHOU -> KUAISHOU_MEDIA_ENDPOINT
        MediaPlatform.DOUBAO -> "https://api-new.ifphp.com/api/doubao"
        MediaPlatform.JIMENG -> "https://api-new.ifphp.com/api/jimeng"
        MediaPlatform.PIPIGX -> "https://api-new.ifphp.com/api/pipigx"
        MediaPlatform.PIPIXIA -> "${BUGPK_API_BASE}pipixia"
        MediaPlatform.XIAOHONGSHU -> "${BUGPK_API_BASE}xhsjx"
        MediaPlatform.ZUIYOU -> "${BUGPK_API_BASE}zuiyou"
        MediaPlatform.TOUTIAO -> "${BUGPK_API_BASE}toutiao"
    }
    val encodedUrl = URLEncoder.encode(sourceUrl, Charsets.UTF_8.name())
    val encodedKey = URLEncoder.encode(BuildConfig.DOUYIN_API_KEY, Charsets.UTF_8.name())
    // The new BugPk documentation endpoints accept the share URL directly. The
    // legacy bp_live key is for the old gateway and is rejected by these routes.
    val requestUrl = if (endpoint.startsWith(BUGPK_API_BASE)) {
        "$endpoint?url=$encodedUrl"
    } else {
        "$endpoint?url=$encodedUrl&key=$encodedKey"
    }
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 12_000
        setRequestProperty("Accept", "application/json")
        if (!endpoint.startsWith(BUGPK_API_BASE)) {
            setRequestProperty("X-API-Key", BuildConfig.DOUYIN_API_KEY)
        }
    }
    try {
        val responseCode = connection.responseCode
        val responseBody = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        if (!responseBody.trimStart().startsWith("{")) {
            throw IllegalStateException("${platform.displayName}解析服务返回异常，请稍后重试")
        }
        val root = JSONObject(responseBody)
        val businessCode = root.optString("code").toIntOrNull()
        if (responseCode !in 200..299 || (businessCode != null && businessCode != 200 && businessCode != 0)) {
            throw IllegalStateException(
                root.optString("msg")
                    .ifBlank { root.optString("message") }
                    .ifBlank { "${platform.displayName}解析服务暂时不可用 ($responseCode)" }
            )
        }
        return root
    } finally {
        connection.disconnect()
    }
}

/**
 * The resolver's `data.url` is often a normal 720P stream even when the post has
 * a much larger original stream. Ask Douyin's public play endpoint for its
 * highest-bitrate redirect and use it when it is materially larger.
 */
private fun upgradeDouyinOriginalStream(data: JSONObject) {
    val videoId = data.optString("video_id").ifBlank { return }
    val apiSize = data.optLong("size").takeIf { it > 0 }
    val encodedVideoId = URLEncoder.encode(videoId, Charsets.UTF_8.name())
    val requestUrl = "$DOUYIN_HIGH_QUALITY_PLAY_ENDPOINT?video_id=$encodedVideoId&ratio=default&line=0&watermark=0&improve_bitrate=1"
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "HEAD"
        connectTimeout = 8_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
        setRequestProperty("Referer", "https://www.douyin.com/")
    }
    try {
        if (connection.responseCode !in 200..299) return
        val highQualitySize = connection.getHeaderFieldLong("Content-Length", -1L).takeIf { it > 0 }
        val materiallyLarger = highQualitySize != null && (apiSize == null || highQualitySize > apiSize * 1.15)
        if (materiallyLarger || (apiSize == null && connection.url.toString() != requestUrl)) {
            data.put("url", connection.url.toString())
            data.put("quality", "原画")
            highQualitySize?.let { data.put("size", it) }
            data.put("size_label", highQualitySize?.let { formatSizeLabel(it) }.orEmpty())
        }
    } catch (_: Exception) {
        // Keep the resolver-provided stream when the high-quality endpoint is unavailable.
    } finally {
        connection.disconnect()
    }
}

private fun formatSizeLabel(sizeBytes: Long): String = when {
    sizeBytes >= 1_024L * 1_024 * 1_024 -> "%.2fGB".format(sizeBytes / (1_024.0 * 1_024 * 1_024))
    sizeBytes >= 1_024L * 1_024 -> "%.2fMB".format(sizeBytes / (1_024.0 * 1_024))
    else -> "%.2fKB".format(sizeBytes / 1_024.0)
}
