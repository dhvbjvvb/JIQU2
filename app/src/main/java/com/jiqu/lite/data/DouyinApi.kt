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
    val pixelResolution: String?
        get() = if (width != null && width > 0 && height != null && height > 0) "$width × $height" else null

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
    val durationMs: Long? = null,
    val caption: String? = null
)

data class MediaAsset(
    val option: MediaDownloadOption,
    val thumbnailUrl: String?,
    val isLive: Boolean = false,
    val isVideo: Boolean = false
)

enum class ParsePhase {
    CONNECTING,
    EXTRACTING
}

private const val BUGPK_API_BASE = "https://api.bugpk.com/api/"
private const val IFPHP_API_BASE = "https://api-new.ifphp.com/api/"
private const val UNIVERSAL_MEDIA_ENDPOINT = "${IFPHP_API_BASE}svparse"
private const val DOUYIN_MEDIA_ENDPOINT = "${IFPHP_API_BASE}dyjx"
private const val WECHAT_CHANNELS_MEDIA_ENDPOINT = "${IFPHP_API_BASE}wxsph"
private const val KUAISHOU_MEDIA_ENDPOINT = "${IFPHP_API_BASE}ksjx"
private const val BILIBILI_MEDIA_ENDPOINT = "${IFPHP_API_BASE}bilibili"
private const val MEDIA_PARSER_API_BASE = BuildConfig.MEDIA_PARSER_API_BASE
private const val MEDIA_PARSER_API_KEY = BuildConfig.MEDIA_PARSER_API_KEY
private const val PARSE_CACHE_DURATION_MS = 5 * 60 * 1_000L
private const val DOUYIN_HIGH_QUALITY_PLAY_ENDPOINT = "https://www.douyin.com/aweme/v1/play/"
private val parseCache = mutableMapOf<String, CachedParse>()
private val parseInFlight = mutableMapOf<String, Deferred<Result<ParsedMedia>>>()
private val parseInFlightMutex = Mutex()
private val parseRequestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class CachedParse(val media: ParsedMedia, val cachedAt: Long)

internal fun endpointRequiresApiKey(endpoint: String): Boolean =
    runCatching {
        URL(endpoint).host.equals("api-new.ifphp.com", ignoreCase = true)
    }.getOrDefault(false)

/** Normalizes resolver payloads where `data` is either an object or an array. */
private fun extractParseData(root: JSONObject): JSONObject? {
    root.optJSONObject("data")?.let { return it }
    val items = root.optJSONArray("data") ?: return null
    if (items.length() == 0) return null
    val first = items.opt(0)
    return when (first) {
        is JSONObject -> {
            if (items.length() == 1) return first
            val merged = JSONObject(first.toString())
            val images = JSONArray()
            val videos = JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val url = item.optString("url")
                    .ifBlank { item.optString("image_url") }
                    .ifBlank { item.optString("image") }
                    .ifBlank { item.optString("img") }
                if (url.isNotBlank()) {
                    if (looksLikeVideoMediaUrl(url)) videos.put(url) else images.put(url)
                }
            }
            if (images.length() > 0 && merged.optJSONArray("images") == null &&
                merged.optJSONArray("imgurl") == null && merged.optJSONArray("image_list") == null
            ) merged.put("images", images)
            if (videos.length() > 0 && merged.optJSONArray("videos") == null) merged.put("videos", videos)
            merged
        }
        is String -> JSONObject().put("images", JSONArray().put(first))
        else -> null
    }
}

/** Reads the user-facing post copy exposed by the three platforms that support it. */
private fun extractCaption(root: JSONObject, data: JSONObject, platform: MediaPlatform): String? {
    if (platform !in setOf(MediaPlatform.DOUYIN, MediaPlatform.KUAISHOU, MediaPlatform.XIAOHONGSHU)) {
        return null
    }
    val captionKeys = listOf("desc", "description", "caption", "content", "share_text", "text", "note_desc")
    val nestedKeys = listOf("item", "aweme_detail", "note", "detail", "post", "note_detail", "note_info", "item_info")

    fun findIn(value: JSONObject): String? {
        captionKeys.asSequence()
            .map { key -> value.optString(key).trim() }
            .firstOrNull { it.isNotBlank() }
            ?.let { return it }
        nestedKeys.asSequence()
            .mapNotNull { key -> value.optJSONObject(key) }
            .forEach { nested -> findIn(nested)?.let { return it } }
        return null
    }

    fun findTitle(value: JSONObject): String? {
        value.optString("title").trim().takeIf {
            it.isNotBlank() && it != "媒体内容" && !it.equals("null", ignoreCase = true)
        }?.let { return it }
        nestedKeys.asSequence()
            .mapNotNull { key -> value.optJSONObject(key) }
            .forEach { nested -> findTitle(nested)?.let { return it } }
        return null
    }

    // Xiaohongshu exposes the user-facing copy in `title`; do not substitute
    // other generic fields such as `desc` or `content` for this platform.
    if (platform == MediaPlatform.XIAOHONGSHU) {
        return findTitle(data) ?: findTitle(root)
    }
    return findIn(data)
        ?: findIn(root)
        ?: findTitle(data)
        ?: findTitle(root)
}

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

internal fun normalizeResolvedMediaUrl(mediaUrl: String): String {
    val normalized = if (mediaUrl.startsWith("//")) "https:$mediaUrl" else mediaUrl
    val parsed = runCatching { URL(normalized) }.getOrNull() ?: return normalized
    val host = parsed.host.lowercase()
    return if (parsed.protocol.equals("http", ignoreCase = true) &&
        (host == "szsttkj.com" || host.endsWith(".szsttkj.com"))
    ) {
        "https://${normalized.substringAfter("://")}"
    } else {
        normalized
    }
}

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
internal fun isPipigxUrl(sourceUrl: String): Boolean = hasHost(
    sourceUrl,
    "pipigx.com",
    "pipigx.cn",
    "ippzone.com"
)
internal fun isPipixiaUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "pipix.com", "pipixia.com")
internal fun isXiaohongshuUrl(sourceUrl: String): Boolean = hasHost(
    sourceUrl,
    "xiaohongshu.com",
    "xhslink.com",
    "xhslink.cn",
    "xiaohongshu.net"
)
internal fun isZuiyouUrl(sourceUrl: String): Boolean = hasHost(
    sourceUrl,
    "izuiyou.com",
    "xiaochuankeji.cn",
    "zuiyou.com"
)
internal fun isToutiaoUrl(sourceUrl: String): Boolean = hasHost(sourceUrl, "toutiao.com", "toutiaovod.com")
internal enum class MediaPlatform(val displayName: String, val requiresApiKey: Boolean = false) {
    DOUYIN("douyin", true),
    BILIBILI("哔哩哔哩", true),
    WECHAT_CHANNELS("微信视频号", true),
    KUAISHOU("快手", true),
    DOUBAO("豆包", true),
    JIMENG("即梦", true),
    // BugPk's current Pipigx endpoint accepts the share URL directly and does
    // not require the app-wide Douyin API key.
    PIPIGX("皮皮搞笑"),
    PIPIXIA("皮皮虾"),
    XIAOHONGSHU("小红书"),
    ZUIYOU("最右"),
    TOUTIAO("今日头条"),
    OTHER("其他平台")
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
    else -> MediaPlatform.OTHER.takeIf {
        runCatching { URL(sourceUrl).protocol.lowercase() in setOf("http", "https") }.getOrDefault(false)
    }
}

internal fun isSupportedMediaUrl(sourceUrl: String): Boolean = mediaPlatformForUrl(normalizeMediaSourceUrl(sourceUrl)) != null

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
        require(platform != null) { "请输入有效链接" }
        synchronized(parseCache) {
            parseCache[normalizedSourceUrl]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt < PARSE_CACHE_DURATION_MS }
                ?.media
        }?.let { return@runCatching it }

        onPhase(ParsePhase.CONNECTING)
        val apiSourceUrl = when (platform) {
            MediaPlatform.DOUYIN -> canonicalizeDouyinSourceUrl(normalizedSourceUrl)
            MediaPlatform.PIPIGX,
            MediaPlatform.ZUIYOU,
            MediaPlatform.PIPIXIA -> resolveShareRedirectUrl(normalizedSourceUrl)
            MediaPlatform.XIAOHONGSHU -> normalizedSourceUrl
            else -> normalizedSourceUrl
        }
        val apiSourceUrls = buildList {
            add(apiSourceUrl)
            add(normalizedSourceUrl)
        }.distinct()
        val candidateResults = coroutineScope {
            List(PARSE_SAMPLE_COUNT) {
                async(Dispatchers.IO) {
                    runCatching {
                        val root = requestParseResultWithRetry(apiSourceUrls, platform!!)
                        onPhase(ParsePhase.EXTRACTING)
                        val data = extractParseData(root) ?: throw IllegalStateException("解析结果为空")
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
            // Pipixia already returns bitrate/duration metadata and its signed CDN
            // is slow to answer extra range probes; keep parsing on the API result.
            if (platform == MediaPlatform.PIPIXIA) candidate
            else candidate.copy(options = enrichDownloadOptionSizes(candidate.options))
        }
        val bestCandidate = sizedCandidates.maxByOrNull { candidateQuality(it.options) }!!
        val root = bestCandidate.root
        val data = bestCandidate.data
        val durationMs = mediaDurationMs(data)
        val mergedOptions = mergeDownloadOptions(sizedCandidates.flatMap { it.options })
        val rawType = data.optString("type").lowercase()
        val hasImageGallery = listOf("images", "imgurl", "image_list", "image_urls", "pictures", "pics")
            .any { key -> data.optJSONArray(key)?.length()?.let { it > 0 } == true }
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
                "live" -> if (hasVideoUrl(data)) "视频" else "图片"
                else -> if (hasImageGallery && !hasVideoUrl(data)) "图片" else "视频"
            },
            fileExtension = primaryOption.fileExtension,
            sizeBytes = largestSize,
            downloadUrl = primaryOption.downloadUrl,
            coverUrl = data.optString("cover")
                .ifBlank { data.optString("cover_url") }
                .ifBlank { data.optString("thumbnail") }
                .ifBlank { data.optString("poster") }
                .takeIf { it.isNotBlank() }
                ?.let(::normalizeResolvedMediaUrl),
            audioUrl = data.optString("music_url")
                .ifBlank { data.optString("audio_url") }
                .ifBlank { data.optString("audio") }
                .ifBlank { music?.optString("url").orEmpty() }
                .takeIf { it.isNotBlank() }
                ?.let(::normalizeResolvedMediaUrl),
            downloadOptions = options,
            assets = assets,
            durationMs = durationMs,
            caption = extractCaption(root, data, platform!!)
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
    fun add(
        url: String,
        thumb: String?,
        live: Boolean,
        format: String = defaultFormat,
        video: Boolean = live
    ) {
        val normalized = normalizeResolvedMediaUrl(url)
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
        assets += MediaAsset(option, thumb?.takeIf { it.isNotBlank() } ?: normalized, live, video)
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
    data.optJSONArray("imgurl")?.let { items ->
        for (i in 0 until items.length()) {
            val url = items.optString(i)
            add(url, url, false, imageFormat(url))
        }
    }
    data.optJSONArray("image_list")?.let { items ->
        for (i in 0 until items.length()) {
            when (val item = items.opt(i)) {
                is String -> add(item, item, false, imageFormat(item))
                is JSONObject -> {
                    val imageUrl = item.optString("url")
                        .ifBlank { item.optString("image_url") }
                        .ifBlank { item.optString("image") }
                    val livePhotoUrl = item.optString("live_photo_url")
                        .ifBlank { item.optString("livePhotoUrl") }
                    if (livePhotoUrl.isNotBlank()) {
                        add(livePhotoUrl, imageUrl, true, "mp4")
                    } else {
                        add(imageUrl, item.optString("cover"), false, imageFormat(imageUrl))
                    }
                }
            }
        }
    }
    listOf("image_urls", "pictures", "pics").forEach { key ->
        data.optJSONArray(key)?.let { items ->
            for (i in 0 until items.length()) {
                when (val item = items.opt(i)) {
                    is String -> add(item, item, false, imageFormat(item))
                    is JSONObject -> {
                        val url = item.optString("url")
                            .ifBlank { item.optString("image") }
                            .ifBlank { item.optString("image_url") }
                        add(url, item.optString("cover").ifBlank { item.optString("thumbnail") }, false, imageFormat(url))
                    }
                }
            }
        }
    }
    liveByImage.values.forEach { (video, image) -> add(video, image, true, "mp4") }
    // Pipi-style APIs may wrap their图文 payload in arbitrary objects (for
    // example `content`, `media` or `item_list`) and use short keys such as
    // `img`/`pic`. Walk those containers so the UI can render every asset.
    fun visitMedia(value: Any?, keyPath: String = "") {
        when (value) {
            is JSONObject -> value.keys().forEach { key ->
                val childPath = if (keyPath.isBlank()) key.lowercase() else "$keyPath.${key.lowercase()}"
                visitMedia(value.opt(key), childPath)
            }
            is JSONArray -> for (i in 0 until value.length()) visitMedia(value.opt(i), keyPath)
            is String -> {
                val normalized = normalizeResolvedMediaUrl(value)
                if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
                val path = keyPath.lowercase()
                if (path.contains("avatar") || path.contains("cover") || path.contains("author") ||
                    path.contains("thumbnail") || path.contains("thumb") || path.contains("poster") ||
                    path.contains("preview")) return
                val looksVideo = path.contains("video") || path.contains("live_photo") ||
                    path.contains("play") || path.contains("download") ||
                    normalized.substringBefore('?').substringAfterLast('.').lowercase() in setOf("mp4", "m3u8", "mov")
                val looksImage = !looksVideo &&
                    (path.contains("image") || path.contains("img") || path.contains("picture") || path.contains("pic") ||
                        normalized.substringBefore('?').substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic"))
                when {
                    looksImage -> add(normalized, normalized, false, imageFormat(normalized))
                    // Video variants remain download options. Do not turn every
                    // signed CDN variant into a separate gallery card.
                    looksVideo -> Unit
                }
            }
        }
    }
    visitMedia(data)
    // When a post mixes images and video, expose the video itself as a
    // selectable cover card as well. Pure-video posts still use the player
    // because the UI ignores asset cards when no image assets are present.
    val cover = data.optString("cover")
        .ifBlank { data.optString("cover_url") }
        .ifBlank { data.optString("thumbnail") }
        .ifBlank { data.optString("poster") }
        .takeIf { it.isNotBlank() }
        ?: assets.firstOrNull { !isVideoUrl(it.option.downloadUrl) }?.thumbnailUrl
    val primaryVideoUrl = data.optString("url")
        .ifBlank { data.optString("video_url") }
        .ifBlank { data.optString("download_url") }
    val responseIsVideo = data.optString("type").lowercase() in setOf("video", "live", "short_video", "movie")
    options.filter { (isVideoUrl(it.downloadUrl) ||
        (responseIsVideo && it.downloadUrl == normalizeResolvedMediaUrl(primaryVideoUrl)) ||
        (primaryVideoUrl.isNotBlank() && it.downloadUrl == normalizeResolvedMediaUrl(primaryVideoUrl) && hasVideoUrl(data)) ||
        (hasVideoUrl(data) && !it.quality.equals("图片", ignoreCase = true) &&
            !it.quality.equals("image", ignoreCase = true)))
    }.forEach { option ->
        if (assets.none { it.option.downloadUrl == option.downloadUrl }) {
            assets += MediaAsset(
                option = option,
                thumbnailUrl = cover,
                isLive = option.quality.contains("实况"),
                isVideo = true
            )
        }
    }
    return assets
}

private fun isVideoUrl(url: String): Boolean {
    val normalized = url.lowercase()
    val path = normalized.substringBefore('?').substringBefore('#')
    return path.endsWith(".mp4") || path.endsWith(".m3u8") ||
        path.endsWith(".mov") || normalized.contains("/video/") ||
        normalized.contains("mime_type=video") || normalized.contains("video_url")
}

private fun looksLikeVideoMediaUrl(url: String): Boolean {
    val normalized = url.lowercase()
    val path = normalized.substringBefore('?').substringBefore('#')
    return path.endsWith(".mp4") || path.endsWith(".m3u8") || path.endsWith(".mov") ||
        normalized.contains("/video/") || normalized.contains("video_url") ||
        normalized.contains("mime_type=video") || normalized.contains("play_addr")
}

private fun hasVideoUrl(data: JSONObject): Boolean {
    val explicitVideoKeys = listOf("video", "video_url", "videoUrl", "play_url", "playUrl", "download_video")
    if (explicitVideoKeys.any { data.optString(it).isNotBlank() }) return true
    if (listOf("url", "download_url").any { key ->
            data.optString(key).let { it.isNotBlank() && looksLikeVideoMediaUrl(it) }
        }) return true
    if (data.optJSONArray("live_photo")?.let { items ->
            (0 until items.length()).any { index ->
                val item = items.optJSONObject(index) ?: return@any false
                item.optString("video").isNotBlank() || item.optString("url").isNotBlank()
            }
        } == true
    ) return true
    return listOf("videos", "video_backup", "video_qualities", "play_addr", "video_info").any { key ->
        data.optJSONArray(key)?.length()?.let { it > 0 } == true
    } || data.optJSONObject("video") != null || data.optJSONObject("play_addr") != null
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
            val host = remoteUrl.host.lowercase()
            when {
                host == "bilivideo.com" || host.endsWith(".bilivideo.com") -> {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
                    setRequestProperty("Referer", "https://www.bilibili.com/")
                }
                host == "ixigua.com" || host.endsWith(".ixigua.com") ||
                    host == "toutiao.com" || host.endsWith(".toutiao.com") -> {
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://www.toutiao.com/")
                    setRequestProperty("Origin", "https://www.toutiao.com")
                }
                host == "ixiaochuan.cn" || host.endsWith(".ixiaochuan.cn") -> {
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://www.pipigx.com/")
                    setRequestProperty("Origin", "https://www.pipigx.com")
                }
                host == "ppxvod.com" || host.endsWith(".ppxvod.com") -> {
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://h5.pipix.com/")
                    setRequestProperty("Origin", "https://h5.pipix.com")
                }
                host == "xhscdn.com" || host.endsWith(".xhscdn.com") ||
                    host == "xhscdn.net" || host.endsWith(".xhscdn.net") -> {
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Referer", "https://www.xiaohongshu.com/")
                    setRequestProperty("Origin", "https://www.xiaohongshu.com")
                }
                else -> setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
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

private fun JSONObject.optLongCompat(vararg keys: String): Long? = keys.asSequence()
    .mapNotNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
    }
    .firstOrNull { it > 0L }

private fun JSONObject.optIntCompat(vararg keys: String): Int? = keys.asSequence()
    .mapNotNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }
    .firstOrNull { it > 0 }

private fun pixelDimensionsFromText(value: String): Pair<Int, Int>? {
    val match = Regex("(\\d{3,5})\\s*[x×*]\\s*(\\d{3,5})", RegexOption.IGNORE_CASE).find(value) ?: return null
    val width = match.groupValues[1].toIntOrNull() ?: return null
    val height = match.groupValues[2].toIntOrNull() ?: return null
    return (width to height).takeIf { width > 0 && height > 0 }
}

private fun JSONObject.optDoubleCompat(vararg keys: String): Double? = keys.asSequence()
    .mapNotNull { key ->
        when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
    }
    .firstOrNull { it > 0.0 }

private fun mediaDurationMs(data: JSONObject): Long? {
    val nested = data.optJSONObject("extra") ?: data.optJSONObject("video")
    data.optLongCompat("duration_ms", "video_duration_ms", "durationMillis")?.let { return it }
    nested?.optLongCompat("duration_ms", "video_duration_ms", "durationMillis")?.let { return it }
    data.optDoubleCompat("duration", "video_duration", "duration_seconds", "durationSec")?.let {
        return durationValueToMs(it)
    }
    nested?.optDoubleCompat("duration", "video_duration", "duration_seconds", "durationSec")?.let {
        return durationValueToMs(it)
    }
    return null
}

private fun durationValueToMs(value: Double): Long =
    if (value >= 1_000.0) value.toLong() else (value * 1_000.0).toLong()

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

private fun resolveShareRedirectUrl(sourceUrl: String): String {
    var current = sourceUrl
    repeat(4) {
        val parsed = runCatching { URL(current) }.getOrNull() ?: return current
        val host = parsed.host.lowercase()
        val shortHost = host == "xhslink.com" || host.endsWith(".xhslink.com") ||
            host == "xhslink.cn" || host.endsWith(".xhslink.cn") ||
            host == "pipigx.com" || host.endsWith(".pipigx.com") ||
            host == "pipigx.cn" || host.endsWith(".pipigx.cn") ||
            host == "ippzone.com" || host.endsWith(".ippzone.com") ||
            host == "izuiyou.com" || host.endsWith(".izuiyou.com")
        if (!shortHost) return current
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4_000
            readTimeout = 5_000
            instanceFollowRedirects = false
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        val next = try {
            if (connection.responseCode !in 300..399) return current
            connection.getHeaderField("Location")?.let { URL(parsed, it).toString() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        } ?: return current
        if (next == current) return current
        current = next
    }
    return current
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
        val normalizedUrl = normalizeResolvedMediaUrl(url)
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
        val playAddress = item.optJSONObject("play_addr") ?: item.optJSONObject("playAddress")
        val textualDimensions = pixelDimensionsFromText(
            item.optString("resolution").ifBlank { item.optString("quality") }.ifBlank { item.optString("label") }
        )
        val width = item.optIntCompat(
            "width", "video_width", "videoWidth", "origin_width", "originWidth", "original_width", "originalWidth"
        ) ?: playAddress?.optIntCompat("width", "video_width", "videoWidth", "origin_width", "originWidth")
            ?: textualDimensions?.first
        val height = item.optIntCompat(
            "height", "video_height", "videoHeight", "origin_height", "originHeight", "original_height", "originalHeight"
        ) ?: playAddress?.optIntCompat("height", "video_height", "videoHeight", "origin_height", "originHeight")
            ?: textualDimensions?.second
        val url = item.optString("url")
            .ifBlank { item.optString("download_url") }
            .ifBlank { item.optString("video_url") }
        if (url.isNotBlank()) {
            addOption(
                url = url,
                quality = item.optString("quality")
                    .ifBlank { item.optString("label") }
                    .ifBlank { acceptedQuality },
                width = width,
                height = height,
                bitRate = item.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps", "video_bitrate", "videoBitrate")
                    ?: playAddress?.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps"),
                sizeBytes = item.optLongCompat("size", "size_bytes"),
                format = item.optString("format")
            )
            return
        }
        val urlList = playAddress?.optJSONArray("url_list") ?: item.optJSONArray("url_list")
        if (urlList != null) {
            for (index in 0 until urlList.length()) {
                addOption(
                    url = urlList.optString(index),
                    quality = item.optString("quality")
                        .ifBlank { item.optString("label") }
                        .ifBlank { acceptedQuality },
                    width = width,
                    height = height,
                    bitRate = item.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps", "video_bitrate", "videoBitrate")
                        ?: playAddress?.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps"),
                    sizeBytes = item.optLongCompat("size", "size_bytes"),
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
    data.optJSONArray("live_photo")?.let { items ->
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val url = item.optString("video")
                .ifBlank { item.optString("url") }
                .ifBlank { item.optString("video_url") }
            addOption(
                url = url,
                quality = item.optString("quality").ifBlank { item.optString("label") }.ifBlank { "原画" },
                width = item.optIntCompat("width", "video_width", "origin_width", "original_width"),
                height = item.optIntCompat("height", "video_height", "origin_height", "original_height"),
                bitRate = item.optLongCompat("bit_rate", "bitrate", "bit_rate_bps"),
                sizeBytes = item.optLongCompat("size", "size_bytes"),
                format = item.optString("format").ifBlank { "mp4" }
            )
        }
    }
    data.optJSONArray("image_list")?.let { items ->
        for (index in 0 until items.length()) {
            when (val item = items.opt(index)) {
                is String -> addOption(item, quality = "图片", format = "jpg")
                is JSONObject -> {
                    val imageUrl = item.optString("url")
                        .ifBlank { item.optString("image_url") }
                        .ifBlank { item.optString("image") }
                    val livePhotoUrl = item.optString("live_photo_url")
                        .ifBlank { item.optString("livePhotoUrl") }
                    addOption(imageUrl, quality = "图片", format = "jpg")
                    addOption(livePhotoUrl, quality = "实况", format = "mp4")
                }
            }
        }
    }
    // Kuaishou and several compatible resolvers nest the original stream in a
    // single `video`/`play_addr` object instead of an array. Preserve all
    // available metadata so the quality picker can show resolution and bitrate.
    listOf("video", "video_info", "play_addr", "playAddress", "original_video").forEach { key ->
        data.optJSONObject(key)?.let { item ->
            addJsonOption(item)
        }
    }
    listOf("image_urls", "pictures", "pics").forEach { key ->
        data.optJSONArray(key)?.let { items ->
            for (index in 0 until items.length()) {
                when (val item = items.opt(index)) {
                    is String -> addOption(item, quality = "图片", format = "jpg")
                    is JSONObject -> addOption(
                        url = item.optString("url").ifBlank { item.optString("image") }.ifBlank { item.optString("image_url") },
                        quality = "图片",
                        width = item.optIntCompat("width"),
                        height = item.optIntCompat("height"),
                        format = item.optString("format").ifBlank { "jpg" }
                    )
                }
            }
        }
    }
    // BugPk XHS responses expose gallery images as `imgurl`.
    data.optJSONArray("imgurl")?.let { items ->
        for (index in 0 until items.length()) {
            items.optString(index).takeIf { it.isNotBlank() }?.let {
                addOption(it, quality = "图片", format = "jpg")
            }
        }
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
                val width = data.optIntCompat("width", "video_width", "origin_width", "original_width") ?: 0
                val height = data.optIntCompat("height", "video_height", "origin_height", "original_height") ?: 0
                if (width >= 2160 || height >= 2160) "原画" else "高清"
            },
        width = data.optIntCompat("width", "video_width", "origin_width", "original_width"),
        height = data.optIntCompat("height", "video_height", "origin_height", "original_height"),
        bitRate = data.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps", "video_bitrate", "videoBitrate"),
        sizeBytes = data.optLongCompat("size", "size_bytes"),
        format = data.optString("format")
    )

    // 快手实况返回的是 live_photo 数组，统一模型当前只支持一个主下载地址。
    if (options.isEmpty()) {
        data.optJSONArray("live_photo")?.optJSONObject(0)?.let { livePhoto ->
            addOption(
                url = livePhoto.optString("video"),
                quality = livePhoto.optString("quality").ifBlank { "实况" },
                width = livePhoto.optIntCompat("width", "video_width", "origin_width", "original_width"),
                height = livePhoto.optIntCompat("height", "video_height", "origin_height", "original_height"),
                bitRate = livePhoto.optLongCompat("bit_rate", "bitrate", "bit_rate_bps", "bitrate_bps"),
                sizeBytes = livePhoto.optLongCompat("size", "size_bytes"),
                format = livePhoto.optString("format").ifBlank { "mp4" }
            )
        }
    }
    if (options.isEmpty()) {
        (data.optJSONArray("images") ?: data.optJSONArray("imgurl"))?.optString(0)?.takeIf { it.isNotBlank() }?.let { imageUrl ->
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
                    val livePhoto = keyPath.contains("live_photo") || keyPath.contains("livephoto")
                    val image = !excludedImage && !livePhoto &&
                        (keyPath.contains("image") ||
                            lower.substringBefore('?').substringAfterLast('.').let { it in setOf("jpg", "jpeg", "png", "webp", "gif") })
                    val video = !image && (livePhoto || keyPath.contains("video") || keyPath.contains("play") ||
                        keyPath.contains("download") ||
                        lower.contains(".mp4") || lower.contains("video"))
                    // Video posts must not expose covers/avatars as download choices.
                    // Image posts keep their image assets while still accepting video/live URLs.
                    if (image || video || (key.isBlank() && !image)) {
                        if (image) {
                            addOption(normalized, "图片", format = "jpg")
                        } else {
                            val original = keyPath.contains("original") || keyPath.contains("origin") ||
                                keyPath.contains("source") || keyPath.contains("best")
                            // Avoid adding anonymous nested URLs as a duplicate 高清 option
                            // when explicit quality candidates are already available.
                            if (original || video || options.isEmpty()) {
                                addOption(normalized, if (original) "原画" else "高清", format = "mp4")
                            }
                        }
                    }
                }
            }
        }
        visit(data)
    }

    val video = data.optJSONObject("video") ?: data.optJSONObject("extra")
    val topLevelDimensions = pixelDimensionsFromText(data.optString("resolution"))
    val fallbackWidth = data.optIntCompat(
        "width", "video_width", "videoWidth", "origin_width", "originWidth", "original_width", "originalWidth"
    ) ?: video?.optIntCompat("width", "video_width", "videoWidth", "origin_width", "originWidth")
        ?: topLevelDimensions?.first
    val fallbackHeight = data.optIntCompat(
        "height", "video_height", "videoHeight", "origin_height", "originHeight", "original_height", "originalHeight"
    ) ?: video?.optIntCompat("height", "video_height", "videoHeight", "origin_height", "originHeight")
        ?: topLevelDimensions?.second
    if (fallbackWidth != null || fallbackHeight != null) {
        options.replaceAll { _, option ->
            val original = option.quality.contains("原画") || option.quality.contains("原始") ||
                option.quality.contains("original", ignoreCase = true) ||
                option.quality.contains("origin", ignoreCase = true) ||
                option.quality.equals("source", ignoreCase = true) ||
                option.quality.equals("raw", ignoreCase = true)
            if (original) {
                option.copy(width = option.width ?: fallbackWidth, height = option.height ?: fallbackHeight)
            } else {
                option
            }
        }
    }

    return options.values.sortedWith(
        compareByDescending<MediaDownloadOption> { resolutionRank(it) }
            .thenByDescending { it.bitRate ?: 0L }
            .thenByDescending { it.sizeBytes ?: 0L }
    )
}

private suspend fun requestParseResultWithRetry(
    sourceUrls: List<String>,
    platform: MediaPlatform
): JSONObject {
    var lastFailure: Throwable? = null
    // These share-link resolvers are comparatively slow and their dedicated
    // endpoints already provide the complete payload. Avoid repeating the same
    // request (and redirect resolution) before falling back to another endpoint.
    val attemptCount = if (platform in setOf(
            MediaPlatform.PIPIXIA,
            MediaPlatform.PIPIGX,
            MediaPlatform.ZUIYOU
        )) 1 else PARSE_ATTEMPT_COUNT
    repeat(attemptCount) { attempt ->
        for (sourceUrl in sourceUrls) {
            for (endpoint in parseEndpoints(platform)) {
                try {
                    return requestParseResult(sourceUrl, platform, endpoint)
                } catch (failure: Throwable) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    lastFailure = failure
                }
            }
        }
        if (attempt + 1 < attemptCount) {
            // BugPk may briefly return global_concurrency_full (429), especially
            // for XHS video notes. Give the gateway a moment before retrying.
            delay(if (platform == MediaPlatform.XIAOHONGSHU) 1_200L else 350L * (attempt + 1))
        }
    }
    throw lastFailure ?: IllegalStateException("${platform.displayName}解析服务暂时不可用")
}

internal fun parseEndpoints(platform: MediaPlatform): List<String> {
    val dedicated = when (platform) {
        MediaPlatform.DOUYIN -> DOUYIN_MEDIA_ENDPOINT
        MediaPlatform.BILIBILI -> BILIBILI_MEDIA_ENDPOINT
        MediaPlatform.WECHAT_CHANNELS -> WECHAT_CHANNELS_MEDIA_ENDPOINT
        MediaPlatform.KUAISHOU -> KUAISHOU_MEDIA_ENDPOINT
        MediaPlatform.DOUBAO -> "${IFPHP_API_BASE}doubao"
        MediaPlatform.JIMENG -> "${IFPHP_API_BASE}jimeng"
        MediaPlatform.PIPIGX -> "${BUGPK_API_BASE}pipigx"
        MediaPlatform.PIPIXIA -> "${BUGPK_API_BASE}pipixia"
        MediaPlatform.XIAOHONGSHU -> "${BUGPK_API_BASE}xhsjx"
        MediaPlatform.ZUIYOU -> "${BUGPK_API_BASE}zuiyou"
        MediaPlatform.TOUTIAO -> "${BUGPK_API_BASE}toutiao"
        MediaPlatform.OTHER -> MEDIA_PARSER_API_BASE
    }
    // Pipigx has two gateways: BugPk accepts the share URL without a key,
    // while the ifphp gateway uses the app key and its platform-specific path.
    if (platform == MediaPlatform.PIPIGX) {
        return listOf(dedicated, "${IFPHP_API_BASE}pipigx", UNIVERSAL_MEDIA_ENDPOINT)
    }
    // Xiaohongshu and unrecognized platforms are handled by the user's
    // universal media-parser gateway. Douyin and Kuaishou keep their legacy
    // dedicated gateway first, then fall back to the universal gateway.
    if (platform == MediaPlatform.DOUYIN || platform == MediaPlatform.KUAISHOU) {
        return listOf(dedicated, MEDIA_PARSER_API_BASE)
    }
    if (platform == MediaPlatform.XIAOHONGSHU || platform == MediaPlatform.OTHER) {
        return listOf(MEDIA_PARSER_API_BASE)
    }
    // Pipixia, Zuiyou and Toutiao are BugPk-only platforms.
    return listOf(dedicated)
}

private fun requestParseResult(sourceUrl: String, platform: MediaPlatform, endpoint: String): JSONObject {
    val isMediaParserEndpoint = endpoint == MEDIA_PARSER_API_BASE
    val requiresApiKey = endpointRequiresApiKey(endpoint)
    if (isMediaParserEndpoint && MEDIA_PARSER_API_KEY.isBlank()) {
        throw IllegalStateException("未配置通用解析密钥")
    }
    if (requiresApiKey && BuildConfig.DOUYIN_API_KEY.isBlank()) {
        throw IllegalStateException("未配置解析密钥")
    }
    val encodedUrl = URLEncoder.encode(sourceUrl, Charsets.UTF_8.name())
    val encodedKey = URLEncoder.encode(BuildConfig.DOUYIN_API_KEY, Charsets.UTF_8.name())
    val encodedMediaParserKey = URLEncoder.encode(MEDIA_PARSER_API_KEY, Charsets.UTF_8.name())
    // BugPk accepts the share URL directly without a key. The ifphp gateway
    // requires the configured bp_live key in both query and header form.
    val requestUrl = if (isMediaParserEndpoint) {
        "$endpoint?key=$encodedMediaParserKey&url=$encodedUrl"
    } else if (!requiresApiKey) {
        "$endpoint?url=$encodedUrl"
    } else {
        "$endpoint?url=$encodedUrl&key=$encodedKey"
    }
    val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 8_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        )
        setRequestProperty(
            "Referer",
            when (platform) {
                MediaPlatform.XIAOHONGSHU -> "https://www.xiaohongshu.com/"
                MediaPlatform.ZUIYOU -> "https://www.izuiyou.com/"
                MediaPlatform.PIPIGX -> "https://www.pipigx.com/"
                MediaPlatform.PIPIXIA -> "https://h5.pipix.com/"
                MediaPlatform.TOUTIAO -> "https://www.toutiao.com/"
                else -> "https://www.douyin.com/"
            }
        )
        if (requiresApiKey) {
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
            ?: root.optString("retcode").toIntOrNull()
        val successFlag = root.optBoolean("succ", true)
        val transientReason = root.optJSONObject("data")?.optString("reason").orEmpty()
        val transientMessage = root.optString("message").ifBlank { root.optString("msg") }
        if (transientReason.contains("concurrency_full", ignoreCase = true) ||
            transientMessage.contains("满载")
        ) {
            throw IllegalStateException("${platform.displayName}解析服务繁忙，请稍后重试")
        }
        if (responseCode !in 200..299 || !successFlag ||
            (businessCode != null && businessCode != 200 && businessCode != 0)
        ) {
            throw IllegalStateException(
                root.optString("msg")
                    .ifBlank { root.optString("message") }
                    .ifBlank { root.optString("retdesc") }
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
