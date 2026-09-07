package com.jiqu.lite

import android.Manifest
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.jiqu.lite.ui.theme.JiquTheme
import com.jiqu.lite.data.ParsedMedia
import com.jiqu.lite.data.MediaDownloadOption
import com.jiqu.lite.data.MediaAsset
import com.jiqu.lite.data.isSupportedMediaUrl
import com.jiqu.lite.data.normalizeMediaSourceUrl
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedInputStream
import java.util.concurrent.Semaphore
import kotlin.math.roundToInt

private enum class AppDestination(val label: String) { Parse("解析"), History("历史"), Settings("设置") }
private enum class SettingsPage { Main, Theme, Downloads, Automation, Tutorials, About }

internal fun destinationIndexForPosition(positionX: Float, width: Float, itemCount: Int): Int {
    if (itemCount <= 1 || width <= 0f) return 0
    return (positionX.coerceIn(0f, width) / (width / itemCount))
        .toInt()
        .coerceIn(0, itemCount - 1)
}

private val httpUrlPattern = Regex("""https?://[^\s<>"'，。！？；、）】}]+""", RegexOption.IGNORE_CASE)

private fun extractHttpUrl(text: String): String? = httpUrlPattern.find(text)
    ?.value
    ?.trimEnd('.', ',', '!', '?', ';', ':', '。', '，', '！', '？', '；', '：', ')', ']', '}')
    ?.takeIf { url -> Uri.parse(url).host?.isNotBlank() == true }
    ?.let(::normalizeMediaSourceUrl)

private data class ParseHistoryEntry(
    val sourceUrl: String,
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
    val parsedAt: Long
)

private class ParseHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("parse_history", Context.MODE_PRIVATE)

    fun load(): List<ParseHistoryEntry> = runCatching {
        val items = JSONArray(preferences.getString(HISTORY_KEY, "[]"))
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val sourceUrl = item.optString("sourceUrl")
                val downloadUrl = item.optString("downloadUrl")
                if (sourceUrl.isNotBlank() && downloadUrl.isNotBlank()) {
                    add(
                        ParseHistoryEntry(
                            sourceUrl = sourceUrl,
                            platform = item.optString("platform").ifBlank { "未知平台" },
                            title = item.optString("title").ifBlank { "媒体内容" },
                            resolution = item.optString("resolution")
                                .ifBlank { item.optString("quality").replace("original", "高清", ignoreCase = true) }
                                .ifBlank { "高清" },
                            bitRate = item.optLong("bitRate").takeIf { it > 0 },
                            mediaType = item.optString("mediaType").ifBlank { "视频" },
                            fileExtension = item.optString("fileExtension").ifBlank { item.optString("format") }.ifBlank { "mp4" },
                            sizeBytes = item.optLong("sizeBytes").takeIf { it > 0 },
                            downloadUrl = downloadUrl,
                            coverUrl = item.optString("coverUrl").ifBlank { null },
                            audioUrl = item.optString("audioUrl").ifBlank { null },
                            parsedAt = item.optLong("parsedAt")
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    fun upsert(sourceUrl: String, media: ParsedMedia): List<ParseHistoryEntry> {
        val latestEntry = ParseHistoryEntry(
            sourceUrl = sourceUrl,
            platform = media.platform,
            title = media.title,
            resolution = media.resolution,
            bitRate = media.bitRate,
            mediaType = media.mediaType,
            fileExtension = media.fileExtension,
            sizeBytes = media.sizeBytes,
            downloadUrl = media.downloadUrl,
            coverUrl = media.coverUrl,
            audioUrl = media.audioUrl,
            parsedAt = System.currentTimeMillis()
        )
        val updatedEntries = (listOf(latestEntry) + load().filterNot {
            it.sourceUrl.equals(sourceUrl, ignoreCase = true)
        }).take(MAX_HISTORY_ENTRIES)
        save(updatedEntries)
        return updatedEntries
    }

    fun delete(sourceUrls: Set<String>): List<ParseHistoryEntry> {
        if (sourceUrls.isEmpty()) return load()
        val updatedEntries = load().filterNot { it.sourceUrl in sourceUrls }
        save(updatedEntries)
        return updatedEntries
    }

    private fun save(entries: List<ParseHistoryEntry>) {
        val items = JSONArray()
        entries.forEach { entry ->
            items.put(
                JSONObject().apply {
                    put("sourceUrl", entry.sourceUrl)
                    put("platform", entry.platform)
                    put("title", entry.title)
                    put("resolution", entry.resolution)
                    put("bitRate", entry.bitRate ?: 0)
                    put("mediaType", entry.mediaType)
                    put("fileExtension", entry.fileExtension)
                    put("sizeBytes", entry.sizeBytes ?: 0)
                    put("downloadUrl", entry.downloadUrl)
                    put("coverUrl", entry.coverUrl.orEmpty())
                    put("audioUrl", entry.audioUrl.orEmpty())
                    put("parsedAt", entry.parsedAt)
                }
            )
        }
        preferences.edit().putString(HISTORY_KEY, items.toString()).apply()
    }

    private companion object {
        const val HISTORY_KEY = "entries"
        const val MAX_HISTORY_ENTRIES = 50
    }
}

private fun formatHistoryTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun platformDisplayName(platform: String): String = when (platform.lowercase()) {
    "douyin", "抖音" -> "抖音"
    "kuaishou", "快手" -> "快手"
    "bilibili", "b站", "哔哩哔哩" -> "哔哩哔哩"
    "xiaohongshu", "小红书" -> "小红书"
    "weibo", "微博" -> "微博"
    "wechat_channel", "视频号", "微信视频号" -> "微信视频号"
    "tiktok" -> "抖音国际版"
    "xigua", "ixigua", "西瓜视频" -> "西瓜视频"
    "toutiao", "今日头条" -> "今日头条"
    "pipixia", "皮皮虾" -> "皮皮虾"
    "doubao", "豆包" -> "豆包"
    "jimeng", "即梦" -> "即梦"
    "pipigx", "皮皮搞笑" -> "皮皮搞笑"
    "youtube" -> "优兔"
    else -> platform.takeIf { it.any { character -> character.code > 127 } } ?: "其他平台"
}

private fun resolutionDisplayName(resolution: String): String {
    val normalized = resolution.trim()
    if (normalized.isBlank()) return "高清"
    return when (normalized.lowercase().replace("_", "").replace("-", "")) {
        "original", "origin", "source", "raw", "原始", "原图" -> "原画"
        "uhd", "2160p", "4k" -> "4K"
        "fhd", "1080p" -> "1080P"
        "hd", "720p" -> "720P"
        "sd", "480p" -> "480P"
        else -> normalized.replace(Regex("original", RegexOption.IGNORE_CASE), "原画")
    }
}

private fun mediaTypeDisplayName(mediaType: String): String = when (mediaType.lowercase()) {
    "实况", "live_photo", "livephoto" -> "实况"
    "图片", "image", "images", "gallery", "photo" -> "图片"
    else -> "视频"
}

private fun formatMediaSize(sizeBytes: Long?): String = when {
    sizeBytes == null || sizeBytes <= 0 -> "待获取"
    sizeBytes >= 1_024L * 1_024 * 1_024 -> "%.2f GB".format(sizeBytes / (1_024.0 * 1_024 * 1_024))
    sizeBytes < 10L * 1_024 * 1_024 -> "%.2f MB".format(sizeBytes / (1_024.0 * 1_024))
    else -> "%.1f MB".format(sizeBytes / (1_024.0 * 1_024))
}

private fun formatBitRate(bitRate: Long?): String = when {
    bitRate == null || bitRate <= 0 -> ""
    bitRate >= 1_000_000 -> "%.2f Mbps".format(bitRate / 1_000_000.0)
    bitRate >= 1_000 -> "%.0f Kbps".format(bitRate / 1_000.0)
    else -> "$bitRate bps"
}

private fun String.isUsableMediaUrl(): Boolean = runCatching {
    val uri = Uri.parse(trim())
    uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private fun applyPreviewAspectTransform(view: TextureView, videoWidth: Int, videoHeight: Int) {
    val viewWidth = view.width
    val viewHeight = view.height
    if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    // Fill the preview surface while preserving the source aspect ratio. The
    // previous fit transform introduced visible bars whenever the stream and
    // the fixed 16:9 preview surface differed by even a small amount.
    val scaleX = if (videoAspect > viewAspect) videoAspect / viewAspect else 1f
    val scaleY = if (videoAspect < viewAspect) viewAspect / videoAspect else 1f
    view.setTransform(
        Matrix().apply {
            setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
        }
    )
}

private val previewBitmapCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}
private val previewLoadSemaphore = Semaphore(4)

private fun loadPreviewBitmap(url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    synchronized(previewBitmapCache) { previewBitmapCache.get(url) }?.let { return it }
    val bitmap = runCatching {
        previewLoadSemaphore.acquire()
        try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        URL(url).openStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sample = calculatePreviewSample(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
        }
        try {
            BufferedInputStream(connection.inputStream).use { BitmapFactory.decodeStream(it, null, options) }
        } finally {
            connection.disconnect()
        }
        } finally {
            previewLoadSemaphore.release()
        }
    }.getOrNull()
    if (bitmap != null) synchronized(previewBitmapCache) { previewBitmapCache.put(url, bitmap) }
    return bitmap
}

private fun calculatePreviewSample(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > 480 || height / sample > 480) sample *= 2
    return sample
}

/** Subtle spring compression shared by compact, directly clickable surfaces. */
private fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "press-scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pointer-pass variant for Material buttons that own their interaction source internally. */
private fun Modifier.pressScaleOnPointer(pressedScale: Float = 0.965f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "pointer-press-scale"
    )
    Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pressed = event.changes.any { it.pressed }
                }
            }
        }
}

class MainActivity : ComponentActivity() {
    private val parseViewModel: ParseViewModel by viewModels()
    private val downloadViewModel: DownloadViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 4001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences = remember {
                getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE)
            }
            var darkTheme by remember {
                mutableStateOf(preferences.getBoolean(AppPreferences.DARK_THEME, false))
            }
            var accentHue by remember {
                mutableFloatStateOf(preferences.getFloat(AppPreferences.ACCENT_HUE, 196f))
            }
            var appleFloatingNav by remember {
                mutableStateOf(preferences.getBoolean(AppPreferences.APPLE_FLOATING_NAV, true))
            }
            var notificationsEnabled by remember {
                mutableStateOf(this@MainActivity.isDownloadCompletionNotificationsEnabled())
            }
            LaunchedEffect(notificationsEnabled) {
                if (notificationsEnabled) {
                    if (hasNotificationPermission()) {
                        ensureDownloadCompletionChannel()
                    } else {
                        requestNotificationPermissionIfNeeded()
                    }
                }
            }
            var autoPasteParseEnabled by remember {
                mutableStateOf(preferences.getBoolean(AppPreferences.AUTO_PASTE_PARSE, true))
            }
            JiquTheme(darkTheme = darkTheme, accentHue = accentHue) {
                JiquApp(
                    darkTheme = darkTheme,
                    accentHue = accentHue,
                    appleFloatingNav = appleFloatingNav,
                    notificationsEnabled = notificationsEnabled,
                    autoPasteParseEnabled = autoPasteParseEnabled,
                    parseUiState = parseViewModel.uiState,
                    onSourceUrlChange = parseViewModel::updateSourceUrl,
                    onParse = parseViewModel::parse,
                    onHistoryRecorded = parseViewModel::markHistoryRecorded,
                    onThemeChange = {
                        darkTheme = it
                        preferences.edit().putBoolean(AppPreferences.DARK_THEME, it).apply()
                    },
                    onAccentHueChange = {
                        accentHue = it
                        preferences.edit().putFloat(AppPreferences.ACCENT_HUE, it).apply()
                    },
                    onAppleFloatingNavChange = {
                        appleFloatingNav = it
                        preferences.edit().putBoolean(AppPreferences.APPLE_FLOATING_NAV, it).apply()
                    },
                    onNotificationsChange = {
                        notificationsEnabled = it
                        preferences.edit().putBoolean(AppPreferences.DOWNLOAD_COMPLETION_NOTIFICATIONS, it).apply()
                    },
                    onAutoPasteParseChange = {
                        autoPasteParseEnabled = it
                        preferences.edit().putBoolean(AppPreferences.AUTO_PASTE_PARSE, it).apply()
                    },
                    onTestNotification = ::sendTestNotification,
                    onOpenDownloadNotificationSettings = ::openDownloadNotificationSettings,
                    onDownload = ::enqueueDownload,
                    onDownloadAudio = ::enqueueAudioDownload,
                    downloadUiState = downloadViewModel.uiState,
                    onDismissDownload = downloadViewModel::dismissResult,
                    updateUiState = updateViewModel.uiState,
                    onCheckForUpdate = { updateViewModel.checkForUpdate(manual = true) },
                    onDismissUpdate = updateViewModel::dismiss,
                    onIgnoreAutomaticUpdates = updateViewModel::ignoreAutomaticChecks,
                    onDownloadUpdate = updateViewModel::chooseDownloadChannel,
                    onSelectUpdateChannel = { update, useGitHub ->
                        updateViewModel.selectUpdateChannel(this@MainActivity, update, useGitHub)
                    },
                    onContinueUpdateInstall = { updateViewModel.continuePendingInstall(this@MainActivity) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModel.resumePendingInstall(this)
        if (isDownloadCompletionNotificationsEnabled() && hasNotificationPermission()) {
            ensureDownloadCompletionChannel()
        }
    }

    override fun onStart() {
        super.onStart()
        updateViewModel.checkForUpdate(manual = false)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            ensureDownloadCompletionChannel()
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureDownloadCompletionChannel() {
        DownloadCompletionNotification.ensureChannel(
            this,
            getSystemService(NotificationManager::class.java)
        )
    }

    private fun sendTestNotification() {
        if (!isDownloadCompletionNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        DownloadCompletionNotification.ensureChannel(this, notificationManager)
        val notification = androidx.core.app.NotificationCompat.Builder(this, DownloadCompletionNotification.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载已完成")
            .setContentText("即取已完成媒体文件下载")
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .build()
        notificationManager.notify(1001, notification)
    }

    private fun openDownloadNotificationSettings() {
        if (!hasNotificationPermission()) {
            requestNotificationPermissionIfNeeded()
            return
        }
        ensureDownloadCompletionChannel()
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, DownloadCompletionNotification.CHANNEL_ID)
            }
        )
    }

    private fun enqueueDownload(media: ParsedMedia, option: MediaDownloadOption) {
        val resolutionSuffix = if (media.assets.isNotEmpty()) {
            "_${option.downloadUrl.hashCode().toString().replace('-', 'n')}"
        } else if (media.downloadOptions.size > 1) {
            val label = if (option.width != null && option.height != null) {
                "${option.width}x${option.height}"
            } else {
                option.quality.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fff_-]"), "_")
            }
            "_${label.take(24)}"
        } else {
            ""
        }
        startDownload(
            media = media,
            downloadUrl = option.downloadUrl,
            format = option.fileExtension,
            suffix = resolutionSuffix,
            expectedSizeBytes = option.sizeBytes
        )
    }

    private fun enqueueAudioDownload(media: ParsedMedia) {
        media.audioUrl?.takeIf(String::isUsableMediaUrl)?.let { audioUrl ->
            val extension = Uri.parse(audioUrl).lastPathSegment
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it in setOf("mp3", "m4a", "aac", "wav", "ogg") }
                ?: "mp3"
            startDownload(media, audioUrl, extension, "_audio", null, targetFolder = "audio")
        }
    }

    private fun startDownload(
        media: ParsedMedia,
        downloadUrl: String,
        format: String,
        suffix: String,
        expectedSizeBytes: Long?,
        targetFolder: String? = null
    ) {
        if (isDownloadCompletionNotificationsEnabled()) {
            requestNotificationPermissionIfNeeded()
        }
        val baseFileName = media.title.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fff._-]"), "_").take(48).ifBlank { "jiqu_media" }
        val fileName = "$baseFileName$suffix"
        downloadViewModel.start(
            DownloadRequest(
                url = downloadUrl,
                fileName = fileName,
                extension = format,
                expectedSizeBytes = expectedSizeBytes,
                targetFolder = targetFolder ?: when (media.mediaType.lowercase()) {
                    "图片", "image", "images", "gallery", "photo" -> DownloadPaths.PICTURES
                    // Live photos are video files and follow the same public video location.
                    "实况", "live_photo", "livephoto", "live" -> DownloadPaths.VIDEO
                    else -> DownloadPaths.VIDEO
                }
            )
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
    }
}

@Composable
private fun JiquApp(
    darkTheme: Boolean,
    accentHue: Float,
    appleFloatingNav: Boolean,
    notificationsEnabled: Boolean,
    autoPasteParseEnabled: Boolean,
    parseUiState: ParseUiState,
    onSourceUrlChange: (String) -> Unit,
    onParse: (String) -> Unit,
    onHistoryRecorded: (Long) -> Unit,
    onThemeChange: (Boolean) -> Unit,
    onAccentHueChange: (Float) -> Unit,
    onAppleFloatingNavChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAutoPasteParseChange: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onOpenDownloadNotificationSettings: () -> Unit,
    onDownload: (ParsedMedia, MediaDownloadOption) -> Unit,
    onDownloadAudio: (ParsedMedia) -> Unit,
    downloadUiState: DownloadUiState = DownloadUiState.Idle,
    onDismissDownload: () -> Unit = {},
    updateUiState: UpdateUiState = UpdateUiState.Hidden,
    onCheckForUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onIgnoreAutomaticUpdates: () -> Unit = {},
    onDownloadUpdate: (AppUpdate) -> Unit = {},
    onSelectUpdateChannel: (AppUpdate, Boolean) -> Unit = { _, _ -> },
    onContinueUpdateInstall: () -> Unit = {}
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Parse.name) }
    val destination = AppDestination.valueOf(destinationName)
    val context = LocalContext.current
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    var lastAutoParsedUrl by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(clipboardManager, lifecycle, autoPasteParseEnabled, parseUiState.parsing) {
        if (clipboardManager == null || lifecycle == null) {
            onDispose { }
        } else {
            fun parseClipboardUrl() {
                if (!autoPasteParseEnabled || parseUiState.parsing) return
                val text = clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
                val url = extractHttpUrl(text)?.takeIf(::isSupportedMediaUrl) ?: return
                if (url == lastAutoParsedUrl) return
                lastAutoParsedUrl = url
                onSourceUrlChange(url)
                onParse(url)
                destinationName = AppDestination.Parse.name
            }
            val listener = ClipboardManager.OnPrimaryClipChangedListener { parseClipboardUrl() }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) parseClipboardUrl()
            }
            clipboardManager.addPrimaryClipChangedListener(listener)
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) parseClipboardUrl()
            onDispose {
                clipboardManager.removePrimaryClipChangedListener(listener)
                lifecycle.removeObserver(observer)
            }
        }
    }
    // Some Android builds dispatch ON_RESUME before the Compose tree finishes attaching.
    // Retry once after startup so a link copied before launching is still handled.
    LaunchedEffect(autoPasteParseEnabled) {
        if (autoPasteParseEnabled) {
            delay(800)
            if (!parseUiState.parsing && destinationName == AppDestination.Parse.name) {
                val text = clipboardManager?.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
                val url = extractHttpUrl(text)?.takeIf(::isSupportedMediaUrl)
                if (url != null && url != lastAutoParsedUrl) {
                    lastAutoParsedUrl = url
                    onSourceUrlChange(url)
                    onParse(url)
                }
            }
        }
    }
    val historyStore = remember(context.applicationContext) { ParseHistoryStore(context.applicationContext) }
    var historyEntries by remember { mutableStateOf(historyStore.load()) }
    LaunchedEffect(parseUiState.successRevision, parseUiState.historyPending) {
        val media = parseUiState.parsedMedia
        val sourceUrl = parseUiState.parsedSourceUrl
        if (parseUiState.historyPending && media != null && sourceUrl != null) {
            historyEntries = historyStore.upsert(sourceUrl, media)
            onHistoryRecorded(parseUiState.successRevision)
        }
    }
    val animatedBackground by androidx.compose.animation.animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        label = "background-color"
    )
    Scaffold(
        containerColor = animatedBackground,
        bottomBar = {
            if (!appleFloatingNav) {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 0.dp
                ) {
                    AppDestination.entries.forEach { item ->
                        NavigationBarItem(
                            modifier = Modifier.pressScaleOnPointer(0.94f),
                            selected = destination == item,
                            onClick = { destinationName = item.name },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            icon = {
                                Icon(
                                    when (item) {
                                        AppDestination.Parse -> Icons.Outlined.Link
                                        AppDestination.History -> Icons.Outlined.History
                                        AppDestination.Settings -> Icons.Outlined.Settings
                                    }, item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(animatedBackground)) {
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                transitionSpec = {
                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(initialOffsetX = { it * direction }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -it * direction }) + fadeOut())
                },
                label = "destination-transition"
            ) { page ->
                when (page) {
                    AppDestination.Parse -> ParseScreen(
                        modifier = Modifier.fillMaxSize(),
                        isActive = true,
                        appleFloatingNav = appleFloatingNav,
                        state = parseUiState,
                        onSourceUrlChange = onSourceUrlChange,
                        onParse = onParse,
                        onDownload = onDownload,
                        onDownloadAudio = onDownloadAudio,
                    )
                    AppDestination.History -> HistoryScreen(
                        entries = historyEntries,
                        appleFloatingNav = appleFloatingNav,
                        onOpen = { sourceUrl ->
                            onSourceUrlChange(sourceUrl)
                            onParse(sourceUrl)
                            destinationName = AppDestination.Parse.name
                        },
                        onDelete = { sourceUrls -> historyEntries = historyStore.delete(sourceUrls) },
                        modifier = Modifier.fillMaxSize()
                    )
                    AppDestination.Settings -> SettingsScreen(
                        isActive = true,
                        darkTheme = darkTheme,
                        accentHue = accentHue,
                        appleFloatingNav = appleFloatingNav,
                        notificationsEnabled = notificationsEnabled,
                        autoPasteParseEnabled = autoPasteParseEnabled,
                        onThemeChange = onThemeChange,
                        onAccentHueChange = onAccentHueChange,
                        onAppleFloatingNavChange = onAppleFloatingNavChange,
                        onNotificationsChange = onNotificationsChange,
                        onAutoPasteParseChange = onAutoPasteParseChange,
                        onTestNotification = onTestNotification,
                        onOpenDownloadNotificationSettings = onOpenDownloadNotificationSettings,
                        isCheckingForUpdate = updateUiState is UpdateUiState.Checking,
                        onCheckForUpdate = onCheckForUpdate,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (appleFloatingNav) {
                FloatingNavigationBar(
                    destination = destination,
                    onDestinationChange = { destinationName = it.name },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
    if (downloadUiState !is DownloadUiState.Idle) {
        DownloadProgressDialog(downloadUiState, onDismissDownload)
    }
    UpdateStatusDialog(
        state = updateUiState,
        onDismiss = onDismissUpdate,
        onRetryCheck = onCheckForUpdate,
        onIgnore = onIgnoreAutomaticUpdates,
        onDownload = onDownloadUpdate,
        onSelectChannel = { update, useGitHub ->
            onSelectUpdateChannel(update, useGitHub)
        },
        onContinueInstall = onContinueUpdateInstall
    )
}

@Composable
private fun ParseScreen(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    appleFloatingNav: Boolean,
    state: ParseUiState,
    onSourceUrlChange: (String) -> Unit,
    onParse: (String) -> Unit,
    onDownload: (ParsedMedia, MediaDownloadOption) -> Unit,
    onDownloadAudio: (ParsedMedia) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val parseStages = remember {
        listOf("正在连接解析服务", "正在提取媒体信息", "正在准备预览")
    }
    var parseStageIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.parsing) {
        if (state.parsing) {
            parseStageIndex = 0
            while (true) {
                delay(900)
                parseStageIndex = (parseStageIndex + 1) % parseStages.size
            }
        }
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppHeader("解析 · 下载", "解析后向下滑动解锁更多")
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(38.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Outlined.Link, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Text("媒体链接", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val clipboardText = clipboardManager.primaryClip
                            ?.getItemAt(0)
                            ?.coerceToText(context)
                            ?.toString()
                            .orEmpty()
                        extractHttpUrl(clipboardText)?.let { url ->
                            onSourceUrlChange(url)
                        }
                    },
                    enabled = !state.parsing,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("粘贴", color = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.sourceUrl,
                onValueChange = { input ->
                    onSourceUrlChange(extractHttpUrl(input) ?: input)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴平台分享链接", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (state.sourceUrl.isNotEmpty()) {
                        IconButton(
                            onClick = { onSourceUrlChange("") },
                            enabled = !state.parsing,
                            modifier = Modifier.pressScaleOnPointer(0.9f)
                        ) { Icon(Icons.Outlined.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                readOnly = state.parsing,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)
                )
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    extractHttpUrl(state.sourceUrl)?.let(onParse)
                },
                enabled = !state.parsing && extractHttpUrl(state.sourceUrl) != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).pressScaleOnPointer(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
            ) {
                if (state.parsing) {
                    CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(9.dp))
                    Text("正在解析", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始解析", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        AnimatedVisibility(
            visible = state.parsing,
            enter = fadeIn() + scaleIn(initialScale = 0.98f),
            exit = fadeOut()
        ) {
            ParseLoadingCard(parseStages[parseStageIndex])
        }
        AnimatedVisibility(
            visible = state.parsedMedia != null,
            enter = fadeIn() + scaleIn(initialScale = 0.96f) + slideInVertically(initialOffsetY = { it / 8 }),
            exit = fadeOut()
        ) { state.parsedMedia?.let { MediaPreviewWindow(it, isActive, onDownload, onDownloadAudio) } }
        AnimatedVisibility(visible = state.errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
            GlassCard { Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(if (appleFloatingNav) 112.dp else 18.dp))
    }
}

@Composable
private fun AppHeader(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ParseLoadingCard(stage: String) {
    val transition = rememberInfiniteTransition(label = "parse-skeleton")
    val skeletonAlpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "parse-skeleton-alpha"
    )
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stage, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("正在加载媒体预览，请稍候", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = skeletonAlpha))
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1.25f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = skeletonAlpha))
                )
            }
        }
    }
}

@Composable
private fun FloatingNavigationBar(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val destinations = AppDestination.entries
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var dragPositionX by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val slotWidthPx = if (barWidthPx > 0f) barWidthPx / destinations.size else 0f
    val restingPosition = destination.ordinal * slotWidthPx
    val indicatorTarget = if (isDragging) {
        (dragPositionX - slotWidthPx / 2f).coerceIn(0f, (barWidthPx - slotWidthPx).coerceAtLeast(0f))
    } else {
        restingPosition
    }
    val indicatorPosition by animateFloatAsState(
        targetValue = indicatorTarget,
        animationSpec = if (isDragging) tween(45) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "floating-navigation-indicator"
    )

    fun selectAt(positionX: Float) {
        dragPositionX = positionX.coerceIn(0f, barWidthPx)
        val target = destinations[destinationIndexForPosition(positionX, barWidthPx, destinations.size)]
        onDestinationChange(target)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 14.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .onSizeChanged { barWidthPx = it.width.toFloat() }
                    .pointerInput(barWidthPx) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                isDragging = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectAt(position.x)
                            },
                            onDragCancel = { isDragging = false },
                            onDragEnd = { isDragging = false },
                            onDrag = { change, _ ->
                                selectAt(change.position.x)
                                change.consume()
                            }
                        )
                    }
            ) {
                if (slotWidthPx > 0f) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(indicatorPosition.roundToInt(), 0) }
                            .width(with(density) { slotWidthPx.toDp() })
                            .fillMaxHeight()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 0.dp
                        ) {}
                    }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { item ->
                        val selected = destination == item
                        val contentColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val interactionSource = remember(item) { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { onDestinationChange(item) }
                                )
                                .padding(vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (item) {
                                    AppDestination.Parse -> Icons.Outlined.Link
                                    AppDestination.History -> Icons.Outlined.History
                                    AppDestination.Settings -> Icons.Outlined.Settings
                                },
                                contentDescription = item.label,
                                tint = contentColor
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label,
                                color = contentColor,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.16f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { Column(Modifier.padding(18.dp), content = content) }
}

private fun selectPreviewOption(media: ParsedMedia): MediaDownloadOption {
    val optionsWithDimensions = media.downloadOptions.filter {
        (it.width ?: 0) > 0 && (it.height ?: 0) > 0
    }
    if (optionsWithDimensions.isNotEmpty()) {
        return optionsWithDimensions.minWith(
            compareBy<MediaDownloadOption> {
                (it.width ?: 0).toLong() * (it.height ?: 0).toLong()
            }.thenBy { it.bitRate ?: Long.MAX_VALUE }
        )
    }
    return media.downloadOptions.filter { (it.bitRate ?: 0) > 0 }
        .minByOrNull { it.bitRate ?: Long.MAX_VALUE }
        ?: media.downloadOptions.last()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaPreviewWindow(
    media: ParsedMedia,
    isActive: Boolean,
    onDownload: (ParsedMedia, MediaDownloadOption) -> Unit,
    onDownloadAudio: (ParsedMedia) -> Unit
) {
    val previewOption = remember(media.downloadOptions) { selectPreviewOption(media) }
    val previewUrl = previewOption.downloadUrl
    val assets = media.assets
    var selectedAssets by remember(media.assets) { mutableStateOf(emptySet<Int>()) }
    var showDownloadDialog by remember(media.downloadUrl) { mutableStateOf(false) }
    var previewPlayer by remember(previewUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(previewUrl) { mutableStateOf(false) }
    var hasPreviewFrame by remember(previewUrl) { mutableStateOf(false) }
    var previewFailed by remember(previewUrl) { mutableStateOf(false) }
    var isPlaying by remember(previewUrl) { mutableStateOf(false) }
    var playWhenReady by remember(previewUrl) { mutableStateOf(false) }
    var playbackPosition by remember(previewUrl) { mutableIntStateOf(0) }
    var duration by remember(previewUrl) {
        mutableIntStateOf(media.durationMs?.toInt()?.coerceAtLeast(0) ?: 0)
    }
    val previewVideoAlpha by animateFloatAsState(
        targetValue = if (hasPreviewFrame) 1f else 0f,
        animationSpec = tween(180),
        label = "preview-video-alpha"
    )
    var coverBitmap by remember(media.coverUrl) {
        mutableStateOf(media.coverUrl?.let { url -> synchronized(previewBitmapCache) { previewBitmapCache.get(url) } })
    }

    LaunchedEffect(media.coverUrl) {
        coverBitmap = withContext(Dispatchers.IO) { loadPreviewBitmap(media.coverUrl) }
    }

    LaunchedEffect(previewPlayer, isPrepared, isPlaying) {
        while (isPrepared && isPlaying) {
            playbackPosition = previewPlayer?.currentPosition ?: playbackPosition
            delay(250)
        }
    }
    // Some signed Douyin streams report zero briefly even after preparation.
    // Keep the API duration visible immediately, then replace it with the
    // player value as soon as the stream exposes reliable metadata.
    LaunchedEffect(previewPlayer, isPrepared) {
        val player = previewPlayer ?: return@LaunchedEffect
        while (isPrepared) {
            val playerDuration = runCatching { player.duration }.getOrDefault(0)
            if (playerDuration > 0) {
                duration = playerDuration
                break
            }
            delay(500)
        }
    }
    // A network-backed MediaPlayer may take several seconds to prepare. Keep
    // the user's play request and start as soon as preparation completes.
    LaunchedEffect(previewPlayer, isPrepared, playWhenReady) {
        if (isPrepared && playWhenReady) {
            previewPlayer?.let { player ->
                if (!player.isPlaying) {
                    runCatching { player.start() }
                    isPlaying = true
                }
            }
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            previewPlayer?.let { player ->
                if (player.isPlaying) {
                    playbackPosition = player.currentPosition
                    player.pause()
                }
            }
            isPlaying = false
            playWhenReady = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassCard {
            Text(
                "媒体预览",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            if (assets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("已解析 ${assets.size} 项，点击卡片选择或取消", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    assets.chunked(3).forEachIndexed { rowIndex, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEachIndexed { colIndex, asset ->
                                val index = rowIndex * 3 + colIndex
                                MediaAssetCard(
                                    asset = asset,
                                    selected = index in selectedAssets,
                                    onClick = {
                                        selectedAssets = if (index in selectedAssets) selectedAssets - index else selectedAssets + index
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholderUrl = media.coverUrl
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
            key(previewUrl) {
                AndroidView(
                    factory = { viewContext ->
                        TextureView(viewContext).apply {
                            var previewSurface: Surface? = null
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                    val surface = Surface(texture)
                                    previewSurface = surface
                                    val player = MediaPlayer()
                                    previewPlayer = player
                                    player.setSurface(surface)
                                    applyPreviewAspectTransform(
                                        this@apply,
                                        previewOption.width ?: 0,
                                        previewOption.height ?: 0
                                    )
                                    player.setOnPreparedListener {
                                        player.duration.takeIf { it > 0 }?.let { duration = it }
                                        playbackPosition = 0
                                        isPlaying = false
                                        isPrepared = true
                                        applyPreviewAspectTransform(
                                            this@apply,
                                            player.videoWidth,
                                            player.videoHeight
                                        )
                                        player.setOnSeekCompleteListener { hasPreviewFrame = true }
                                        player.seekTo(1, MediaPlayer.SEEK_CLOSEST)
                                    }
                                    player.setOnVideoSizeChangedListener { _, width, height ->
                                        applyPreviewAspectTransform(this@apply, width, height)
                                    }
                                    player.setOnInfoListener { _, what, _ ->
                                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                            hasPreviewFrame = true
                                        }
                                        false
                                    }
                                    player.setOnCompletionListener {
                                        playbackPosition = duration
                                        isPlaying = false
                                        playWhenReady = false
                                    }
                                    player.setOnErrorListener { _, _, _ ->
                                        previewFailed = true
                                        isPrepared = false
                                        isPlaying = false
                                        playWhenReady = false
                                        true
                                    }
                                    runCatching {
                                        player.setDataSource(
                                            viewContext,
                                            Uri.parse(previewUrl),
                                            mapOf(
                                                "User-Agent" to "Mozilla/5.0 (Android) Jiqu/1.0",
                                                "Referer" to "https://www.douyin.com/"
                                            )
                                        )
                                        player.prepareAsync()
                                    }.onFailure {
                                        previewFailed = true
                                        isPrepared = false
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    previewPlayer?.let { player ->
                                        player.release()
                                        if (previewPlayer === player) previewPlayer = null
                                    }
                                    previewSurface?.release()
                                    previewSurface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().alpha(previewVideoAlpha),
                    onRelease = {
                        previewPlayer?.release()
                        previewPlayer = null
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - previewVideoAlpha)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                    coverBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "媒体预览封面",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } ?: if (previewFailed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "暂时无法预览，可直接下载",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .semantics { contentDescription = "预览加载中" },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
            }
        }
        Spacer(Modifier.height(12.dp))
        MediaDetails(media)
        if (assets.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                onClick = {
                    if (!previewFailed) {
                        playWhenReady = !playWhenReady
                        previewPlayer?.let { player ->
                            if (isPrepared && player.isPlaying) {
                                playbackPosition = player.currentPosition
                                player.pause()
                                isPlaying = false
                                playWhenReady = false
                            } else if (isPrepared) {
                                player.start()
                                isPlaying = true
                            }
                        }
                    }
                },
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !previewFailed,
                color = if (!previewFailed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (!previewFailed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying || (playWhenReady && !isPrepared)) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isPlaying || (playWhenReady && !isPrepared)) "暂停" else "播放", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${formatPlaybackTime(playbackPosition)} / ${formatPlaybackTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = playbackPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { position ->
                    playbackPosition = position.toInt().coerceIn(0, duration)
                    previewPlayer?.seekTo(playbackPosition)
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = isPrepared,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                thumb = { PreviewSliderThumb(enabled = isPrepared) },
                track = {
                    PreviewSliderTrack(
                        progress = playbackPosition.toFloat() / duration.coerceAtLeast(1),
                        enabled = isPrepared
                    )
                }
            )
            }
        }
            Spacer(Modifier.height(14.dp))
            Button(onClick = {
                if (assets.isNotEmpty()) {
                    selectedAssets.mapNotNull { assets.getOrNull(it)?.option }.forEach { onDownload(media, it) }
                } else showDownloadDialog = true
            }, enabled = assets.isEmpty() || selectedAssets.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScaleOnPointer(), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Outlined.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (assets.isNotEmpty()) "下载已选媒体（${selectedAssets.size}）" else "下载媒体")
            }
        }
        media.audioUrl?.takeIf(String::isUsableMediaUrl)?.let { audioUrl ->
            AudioPreviewCard(audioUrl = audioUrl, onDownload = { onDownloadAudio(media) })
        }
    }
    if (showDownloadDialog) {
        DownloadQualityDialog(
            options = media.downloadOptions,
            onDismiss = { showDownloadDialog = false },
            onDownload = { option ->
                showDownloadDialog = false
                onDownload(media, option)
            }
        )
    }
}

@Composable
private fun MediaAssetCard(
    asset: MediaAsset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholderUrl: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pressScale(interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = if (selected) "媒体缩略图，已选中" else "媒体缩略图"
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        RemoteImage(asset.thumbnailUrl, Modifier.fillMaxSize(), placeholderUrl)
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) { Text("✓", modifier = Modifier.padding(start = 4.dp, top = 0.dp), style = MaterialTheme.typography.labelMedium) }
        }
        if (asset.isLive) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(28.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f),
                contentColor = Color.White
            ) { Icon(Icons.Outlined.PlayArrow, "实况播放", Modifier.padding(6.dp)) }
        }
    }
}

@Composable
private fun RemoteImage(url: String?, modifier: Modifier = Modifier, placeholderUrl: String? = null) {
    if (url.isNullOrBlank()) {
        Box(modifier = modifier.background(Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var loading by remember(url) { mutableStateOf(true) }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (loading) {
            val transition = rememberInfiniteTransition(label = "image-placeholder")
            val placeholderAlpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "image-placeholder-alpha"
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = placeholderAlpha))
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .size(640)
                .crossfade(180)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (loading) 0f else 1f),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            onSuccess = { loading = false },
            onError = {
                loading = false
                // Keep the stable surface placeholder when remote media is unavailable.
            }
        )
    }
}

private fun formatPlaybackTime(milliseconds: Int): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MediaDetails(media: ParsedMedia) {
    val summary = listOf(
        platformDisplayName(media.platform),
        resolutionDisplayName(media.resolution),
        formatBitRate(media.bitRate),
        mediaTypeDisplayName(media.mediaType),
        formatMediaSize(media.sizeBytes)
    ).filter { it.isNotBlank() }.joinToString("  ·  ")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
    ) {
        Text(
            text = summary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun UpdateStatusDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onRetryCheck: () -> Unit,
    onIgnore: () -> Unit,
    onDownload: (AppUpdate) -> Unit,
    onSelectChannel: (AppUpdate, Boolean) -> Unit,
    onContinueInstall: () -> Unit
) {
    if (state is UpdateUiState.Hidden) return
    val blocksDismiss = state is UpdateUiState.Checking || state is UpdateUiState.Downloading
    AlertDialog(
        onDismissRequest = { if (!blocksDismiss) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(
                when (state) {
                    UpdateUiState.Checking -> "检查更新"
                    UpdateUiState.UpToDate -> "检查更新"
                    is UpdateUiState.Available -> "发现新版本"
                    is UpdateUiState.SelectingChannel -> "选择更新渠道"
                    is UpdateUiState.Failed -> "检查更新失败"
                    is UpdateUiState.Downloading -> "正在下载更新"
                    is UpdateUiState.AwaitingInstallPermission -> "允许安装更新"
                    is UpdateUiState.DownloadFailed -> "更新失败"
                    UpdateUiState.Hidden -> ""
                }
            )
        },
        text = {
            when (state) {
                UpdateUiState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在检查更新中")
                    }
                }
                UpdateUiState.UpToDate -> Text("已为最新版本")
                is UpdateUiState.Available -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${BuildConfig.VERSION_NAME}  →  ${state.update.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            buildString {
                                append("更新来源：${state.update.source.displayName}")
                                if (state.update.assetSizeBytes > 0L) {
                                    append("  ·  ${formatMediaSize(state.update.assetSizeBytes)}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.update.versionName == "2.0.6" && BuildConfig.VERSION_NAME != "2.0.6") {
                            Text(
                                "提示：2.0.6 起包名改为 com.jiqu.lite，无法覆盖旧版 com.jiqu.app，请先卸载旧版。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            markdownToAnnotatedString(state.update.releaseNotes),
                            modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is UpdateUiState.SelectingChannel -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("请选择下载方式")
                        OutlinedButton(
                            onClick = { onSelectChannel(state.update, true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("GitHub（应用内下载并安装）")
                        }
                        OutlinedButton(
                            onClick = { onSelectChannel(state.update, false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("蓝奏云（浏览器手动下载）")
                        }
                        Text(
                            "选择蓝奏云后，提取码会自动复制到剪贴板。",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is UpdateUiState.Failed -> Text(state.message)
                is UpdateUiState.Downloading -> {
                    val total = state.totalBytes
                    val progress = if (total > 0L) {
                        (state.downloadedBytes.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
                    } else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.update.assetName, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (total > 0L) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (total > 0L) "${(progress * 100).toInt()}%" else "正在获取文件",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (total > 0L) {
                                    "${formatMediaSize(state.downloadedBytes)} / ${formatMediaSize(total)}"
                                } else {
                                    formatMediaSize(state.downloadedBytes)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is UpdateUiState.AwaitingInstallPermission -> Text(
                    "请在系统设置中允许“即取”安装未知应用，返回后将自动打开安装程序。"
                )
                is UpdateUiState.DownloadFailed -> Text(state.message)
                UpdateUiState.Hidden -> Unit
            }
        },
        dismissButton = {
            when (state) {
                is UpdateUiState.Available -> TextButton(onClick = onIgnore) { Text("忽略") }
                is UpdateUiState.SelectingChannel -> TextButton(onClick = onDismiss) { Text("取消") }
                is UpdateUiState.Failed,
                is UpdateUiState.DownloadFailed -> TextButton(onClick = onDismiss) { Text("关闭") }
                else -> Unit
            }
        },
        confirmButton = {
            when (state) {
                UpdateUiState.UpToDate -> Button(onClick = onDismiss) { Text("确定") }
                is UpdateUiState.Available -> Button(onClick = { onDownload(state.update) }) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("下载最新版本")
                }
                is UpdateUiState.Failed -> Button(onClick = onRetryCheck) { Text("重试") }
                is UpdateUiState.AwaitingInstallPermission -> Button(onClick = onContinueInstall) {
                    Text("前往授权")
                }
                is UpdateUiState.DownloadFailed -> Button(onClick = { onSelectChannel(state.update, true) }) {
                    Text("重新下载")
                }
                else -> Unit
            }
        }
    )
}

/** Renders the Markdown subset used by GitHub release notes without flattening its structure. */
private fun markdownToAnnotatedString(markdown: String): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r\n", "\n").lines()
    lines.forEachIndexed { index, rawLine ->
        if (index > 0) append("\n")
        val line = rawLine.trimEnd()
        val bullet = Regex("^\\s*([-*+])\\s+").find(line)
        val ordered = Regex("^\\s*(\\d+)\\.\\s+").find(line)
        when {
            bullet != null -> {
                append("• ")
                appendMarkdownInline(line.removeRange(0, bullet.range.last + 1).trimStart())
            }
            ordered != null -> {
                append("${ordered.groupValues[1]}. ")
                appendMarkdownInline(line.removeRange(0, ordered.range.last + 1).trimStart())
            }
            line.trimStart().startsWith("#") -> {
                val heading = line.trimStart().trimStart('#').trimStart()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendMarkdownInline(heading) }
            }
            else -> appendMarkdownInline(line)
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(value: String) {
    var index = 0
    while (index < value.length) {
        val rest = value.substring(index)
        val match = Regex("^(\\*\\*|__)(.+?)\\1|^([*_])(.+?)\\3|^`([^`]+)`|^\\[([^]]+)]\\([^)]*\\)").find(rest)
        if (match == null) {
            append(value[index])
            index++
            continue
        }
        when {
            match.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
            match.groupValues[4].isNotEmpty() -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(match.groupValues[4]) }
            match.groupValues[5].isNotEmpty() -> withStyle(SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) { append(match.groupValues[5]) }
            match.groupValues[6].isNotEmpty() -> withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF1976D2), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(match.groupValues[6]) }
        }
        index += match.value.length
    }
}

@Composable
private fun DownloadProgressDialog(
    state: DownloadUiState,
    onDismiss: () -> Unit
) {
    val canDismiss = state is DownloadUiState.Success || state is DownloadUiState.Failure
    val isProcessing = state is DownloadUiState.Preparing || state is DownloadUiState.Running
    val pulseTransition = rememberInfiniteTransition(label = "download-status-pulse")
    val statusAlpha by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "download-status-alpha"
    )
    AlertDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(
                when (state) {
                    is DownloadUiState.Success -> "下载完成"
                    is DownloadUiState.Failure -> "下载失败"
                    else -> "正在下载"
                },
                modifier = Modifier.alpha(if (isProcessing) statusAlpha else 1f)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val fileName = when (state) {
                    is DownloadUiState.Preparing -> state.fileName
                    is DownloadUiState.Running -> state.fileName
                    is DownloadUiState.Success -> state.fileName
                    is DownloadUiState.Failure -> state.fileName
                    DownloadUiState.Idle -> ""
                }
                Text(
                    fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                when (state) {
                    is DownloadUiState.Preparing -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在获取文件信息…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is DownloadUiState.Running -> {
                        val determinate = state.totalBytes > 0
                        val progress = if (determinate) {
                            // Keep 100% for the completion state. Using Float here
                            // rounds (total - 1) to total for large files.
                            if (state.downloadedBytes >= state.totalBytes) {
                                1f
                            } else {
                                (state.downloadedBytes.toDouble() / state.totalBytes.toDouble())
                                    .coerceIn(0.0, 0.99)
                                    .toFloat()
                            }
                        } else 0f
                        if (determinate) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (determinate) "${(progress * 100).toInt()}%" else "计算中",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${formatMediaSize(state.downloadedBytes)} / ${formatMediaSize(state.totalBytes.takeIf { it > 0 })}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${state.threadCount} 个下载线程",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadUiState.Success -> {
                        LinearProgressIndicator(
                            progress = { 1f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("已保存到 Download/JIQU，并清理下载缓存。")
                    }
                    is DownloadUiState.Failure -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Text(
                            "未完成文件和分片缓存已清理。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadUiState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            if (canDismiss) {
                Button(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DownloadQualityDialog(
    options: List<MediaDownloadOption>,
    onDismiss: () -> Unit,
    onDownload: (MediaDownloadOption) -> Unit
) {
    var selectedIndex by remember(options) { mutableIntStateOf(0) }
    var showLargeFileConfirm by rememberSaveable { mutableStateOf(false) }
    val selectedOption = options.getOrNull(selectedIndex)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Column {
                Text("选择下载清晰度", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (options.size > 1) "共 ${options.size} 种规格，可选择不同分辨率、码率和文件大小" else "当前链接仅返回 1 种可下载规格",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    val resolution = resolutionDisplayName(option.resolution)
                    val details = listOf(
                        resolutionDisplayName(option.quality).takeUnless { it == resolution }.orEmpty(),
                        formatBitRate(option.bitRate),
                        formatMediaSize(option.sizeBytes).takeUnless { it == "待获取" }.orEmpty()
                    ).filter { it.isNotBlank() }.joinToString("  ·  ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selectedIndex == index) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            )
                            .border(
                                width = 1.dp,
                                color = if (selectedIndex == index) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                                },
                                shape = RoundedCornerShape(18.dp)
                            )
                            .pressScale(interactionSource, pressedScale = 0.98f)
                            .clickable(interactionSource = interactionSource, indication = null) { selectedIndex = index }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolution,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (details.isNotBlank()) {
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val size = selectedOption?.sizeBytes ?: 0L
                    if (size >= 2L * 1_024 * 1_024 * 1_024) {
                        showLargeFileConfirm = true
                    } else {
                        selectedOption?.let(onDownload)
                    }
                },
                enabled = selectedOption != null,
                modifier = Modifier.pressScaleOnPointer(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("下载") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) { Text("取消") }
        }
    )
    if (showLargeFileConfirm && selectedOption != null) {
        AlertDialog(
            onDismissRequest = { showLargeFileConfirm = false },
            title = { Text("确认下载大文件") },
            text = {
                Text("当前文件约 ${formatMediaSize(selectedOption.sizeBytes)}，下载可能耗时较长并消耗较多流量。是否继续？")
            },
            confirmButton = {
                Button(onClick = { showLargeFileConfirm = false; onDownload(selectedOption) }) { Text("继续下载") }
            },
            dismissButton = {
                TextButton(onClick = { showLargeFileConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun PreviewSliderThumb(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .offset(y = 2.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
            )
    )
}

@Composable
private fun PreviewSliderTrack(progress: Float, enabled: Boolean) {
    val activeColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    }
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.14f else 0.08f)
    Box(
        modifier = Modifier.fillMaxWidth().height(10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(inactiveColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .clip(CircleShape)
                .background(activeColor)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPreviewCard(audioUrl: String, onDownload: () -> Unit) {
    var mediaPlayer by remember(audioUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(audioUrl) { mutableStateOf(false) }
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var playbackPosition by remember(audioUrl) { mutableIntStateOf(0) }
    var duration by remember(audioUrl) { mutableIntStateOf(0) }

    DisposableEffect(audioUrl) {
        val player = MediaPlayer()
        player.setOnPreparedListener {
            duration = it.duration.coerceAtLeast(0)
            isPrepared = true
        }
        player.setOnCompletionListener {
            playbackPosition = duration
            isPlaying = false
        }
        player.setOnErrorListener { _, _, _ ->
            isPlaying = false
            true
        }
        runCatching {
            player.setDataSource(audioUrl)
            player.prepareAsync()
        }
        mediaPlayer = player
        onDispose {
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
        }
    }

    LaunchedEffect(mediaPlayer, isPrepared, isPlaying) {
        while (isPrepared && isPlaying) {
            playbackPosition = mediaPlayer?.currentPosition ?: playbackPosition
            delay(250)
        }
    }

    GlassCard {
        Text("音频预览", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text("作品原声", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = {
                    if (isPrepared) {
                        mediaPlayer?.let { player ->
                            if (player.isPlaying) {
                                playbackPosition = player.currentPosition
                                player.pause()
                                isPlaying = false
                            } else {
                                player.start()
                                isPlaying = true
                            }
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isPrepared) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isPrepared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(if (isPlaying) "暂停" else "播放", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${formatPlaybackTime(playbackPosition)} / ${formatPlaybackTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = playbackPosition.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
                onValueChange = { position ->
                    playbackPosition = position.toInt().coerceIn(0, duration)
                    mediaPlayer?.seekTo(playbackPosition)
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                enabled = isPrepared,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                thumb = { PreviewSliderThumb(enabled = isPrepared) },
                track = {
                    PreviewSliderTrack(
                        progress = playbackPosition.toFloat() / duration.coerceAtLeast(1),
                        enabled = isPrepared
                    )
                }
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScaleOnPointer(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Outlined.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("下载音频")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryScreen(
    entries: List<ParseHistoryEntry>,
    appleFloatingNav: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedSourceUrls by remember { mutableStateOf(emptySet<String>()) }

    fun toggleSelection(sourceUrl: String) {
        selectedSourceUrls = if (sourceUrl in selectedSourceUrls) {
            selectedSourceUrls - sourceUrl
        } else {
            selectedSourceUrls + sourceUrl
        }
    }

    fun handleEntryClick(entry: ParseHistoryEntry) {
        if (multiSelectMode) {
            toggleSelection(entry.sourceUrl)
        } else {
            selectedSourceUrls = emptySet()
            onOpen(entry.sourceUrl)
        }
    }

    fun enterMultiSelect(entry: ParseHistoryEntry) {
        multiSelectMode = true
        selectedSourceUrls = selectedSourceUrls + entry.sourceUrl
    }

    BackHandler(enabled = multiSelectMode) {
        selectedSourceUrls = emptySet()
        multiSelectMode = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("历史记录", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(3.dp))
                Text(
                    "长按任意历史记录进入多选",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (multiSelectMode) {
                Text(
                    "${selectedSourceUrls.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(
                onClick = {
                    onDelete(selectedSourceUrls)
                    selectedSourceUrls = emptySet()
                    multiSelectMode = false
                },
                enabled = selectedSourceUrls.isNotEmpty(),
                modifier = Modifier.pressScaleOnPointer(0.9f)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除选中的历史记录")
            }
        }
        if (entries.isEmpty()) {
            GlassCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.History, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("暂无解析记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            entries.forEach { entry ->
                val selected = entry.sourceUrl in selectedSourceUrls
                val cardShape = RoundedCornerShape(24.dp)
                val interactionSource = remember(entry.sourceUrl) { MutableInteractionSource() }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(interactionSource, pressedScale = 0.98f)
                        .shadow(
                            elevation = if (selected) 10.dp else 16.dp,
                            shape = cardShape,
                            ambientColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f),
                            spotColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)
                        )
                        .clip(cardShape)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { handleEntryClick(entry) },
                            onLongClick = { enterMultiSelect(entry) }
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            shape = cardShape
                        ),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            entry.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = listOf(
                                formatHistoryTime(entry.parsedAt),
                                platformDisplayName(entry.platform),
                                resolutionDisplayName(entry.resolution),
                                formatBitRate(entry.bitRate),
                                mediaTypeDisplayName(entry.mediaType),
                                formatMediaSize(entry.sizeBytes)
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            entry.sourceUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(if (appleFloatingNav) 112.dp else 18.dp))
    }
}

@Composable
private fun SettingsScreen(
    isActive: Boolean,
    darkTheme: Boolean,
    accentHue: Float,
    appleFloatingNav: Boolean,
    notificationsEnabled: Boolean,
    autoPasteParseEnabled: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onAccentHueChange: (Float) -> Unit,
    onAppleFloatingNavChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onAutoPasteParseChange: (Boolean) -> Unit,
    onTestNotification: () -> Unit,
    onOpenDownloadNotificationSettings: () -> Unit,
    isCheckingForUpdate: Boolean,
    onCheckForUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notificationSent by rememberSaveable { mutableStateOf(false) }
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.Main.name) }
    val page = SettingsPage.valueOf(pageName)
    val context = LocalContext.current
    val sourceUrl = "https://github.com/dhvbjvvb/JIQU2"
    BackHandler(enabled = isActive && page != SettingsPage.Main) { pageName = SettingsPage.Main.name }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (page == SettingsPage.Main) {
            AppHeader("设置")
            SettingsEntry(
                icon = Icons.Outlined.Palette,
                title = "主题与外观",
                summary = "${if (darkTheme) "深色" else "浅色"} · 自定义主题颜色",
                onClick = { pageName = SettingsPage.Theme.name }
            )
            SettingsEntry(
                icon = Icons.Outlined.Download,
                title = "下载与通知",
                summary = "${DownloadPaths.DISPLAY_ROOT} · 完成提醒 ${if (notificationsEnabled) "已开启" else "已关闭"}",
                onClick = { pageName = SettingsPage.Downloads.name }
            )
            SettingsEntry(
                icon = Icons.Outlined.Search,
                title = "自动粘贴与解析",
                summary = if (autoPasteParseEnabled) "已开启 · 进入应用自动识别链接" else "已关闭",
                onClick = { pageName = SettingsPage.Automation.name }
            )
            SettingsEntry(
                icon = Icons.Outlined.SystemUpdate,
                title = "检查更新",
                summary = if (isCheckingForUpdate) "正在检查更新中" else "当前版本 ${BuildConfig.VERSION_NAME}",
                onClick = onCheckForUpdate
            )
            SettingsEntry(
                icon = Icons.Outlined.Info,
                title = "解析教程与支持",
                summary = "查看支持的 APP 与分享链接教程",
                onClick = { pageName = SettingsPage.Tutorials.name }
            )
            SettingsEntry(
                icon = Icons.Outlined.Info,
                title = "关于本 APP",
                summary = "制作人：春日大阪",
                onClick = { pageName = SettingsPage.About.name }
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pageName = SettingsPage.Main.name }, modifier = Modifier.pressScaleOnPointer(0.9f)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回设置")
                }
                Text(
                    text = when (page) {
                        SettingsPage.Theme -> "主题与外观"
                        SettingsPage.Downloads -> "下载与通知"
                        SettingsPage.Automation -> "自动粘贴与解析"
                        SettingsPage.Tutorials -> "解析教程与支持"
                        SettingsPage.About -> "关于本 APP"
                        SettingsPage.Main -> "设置"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (page == SettingsPage.Theme) {
                GlassCard {
                    Text("主题模式", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeChoice("浅色", selected = !darkTheme, onClick = { onThemeChange(false) }, modifier = Modifier.weight(1f))
                        ThemeChoice("深色", selected = darkTheme, onClick = { onThemeChange(true) }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("主题颜色", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text("拖动取色条，全局应用到按钮、导航和选中状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    HuePicker(value = accentHue, onValueChange = onAccentHueChange)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(22.dp), shape = CircleShape, color = Color.hsv(accentHue, 0.68f, 0.62f)) {}
                        Spacer(Modifier.width(8.dp))
                        Text("色相 ${accentHue.toInt()}°", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Apple 风格悬浮底栏", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("将底部导航显示为悬浮圆角样式", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = appleFloatingNav, onCheckedChange = onAppleFloatingNavChange)
                    }
                }
            } else if (page == SettingsPage.Downloads) {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Notifications, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("下载完成通知", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("下载结束后显示系统提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChange)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onTestNotification(); notificationSent = true },
                        enabled = notificationsEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScaleOnPointer(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (notificationSent) "已发送测试通知" else "发送测试通知") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenDownloadNotificationSettings,
                        enabled = notificationsEnabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScaleOnPointer(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("设置通知声音和振动") }
                }
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Folder, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("公共下载目录", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(DownloadPaths.DISPLAY_ROOT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DownloadPathRow("实况", "${DownloadPaths.DISPLAY_ROOT} / ${DownloadPaths.VIDEO}")
                    DownloadPathRow("视频", "${DownloadPaths.DISPLAY_ROOT} / ${DownloadPaths.VIDEO}")
                    DownloadPathRow("图片", "${DownloadPaths.DISPLAY_ROOT} / ${DownloadPaths.PICTURES}")
                }
            } else if (page == SettingsPage.Tutorials) {
                TutorialSupportContent()
            } else if (page == SettingsPage.About) {
                GlassCard {
                    Text("关于本 APP", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    Text("问题反馈", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("1515068599@qq.com", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    Text("制作人", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("春日大阪", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    Text("开源地址", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sourceUrl,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl))
                                context.startActivity(Intent.createChooser(browserIntent, "选择浏览器打开"))
                            }
                    )
                }
            } else {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Search, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("自动粘贴并解析", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("进入应用时识别剪贴板中的平台链接并开始解析", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoPasteParseEnabled, onCheckedChange = onAutoPasteParseChange)
                    }
                }
            }
        }
        Spacer(Modifier.height(if (appleFloatingNav) 112.dp else 18.dp))
    }
}

private data class TutorialSupportItem(
    val name: String,
    val iconRes: Int,
    val capability: String,
    val tutorial: String
)

@Composable
private fun TutorialSupportContent() {
    val items = listOf(
        TutorialSupportItem(
            "快手", R.drawable.icon_kuaishou,
            "快手视频、图集、实况去水印解析",
            "复制 APP 内的分享链接，回到本 APP 粘贴解析即可"
        ),
        TutorialSupportItem(
            "抖音", R.drawable.icon_douyin,
            "抖音去水印解析，支持图文、短视频和实况解析",
            "复制 APP 内的分享链接，回到本 APP 粘贴解析即可"
        ),
        TutorialSupportItem(
            "皮皮搞笑", R.drawable.icon_pipi,
            "皮皮搞笑无水印解析",
            "复制 APP 内的分享链接，回到本 APP 粘贴解析即可"
        ),
        TutorialSupportItem(
            "即梦", R.drawable.icon_jimeng,
            "即梦 AI 视频去水印，支持隐藏和未发布的作品",
            "复制 APP 内的分享链接，回到本 APP 粘贴解析即可"
        ),
        TutorialSupportItem(
            "豆包", R.drawable.icon_doubao,
            "豆包对话生图、视频去水印解析",
            "长按豆包生成的视频或者图片对话，选择“分享”，选择“分享链接”，回到本 APP 解析即可"
        ),
        TutorialSupportItem(
            "微信", R.drawable.icon_wechat,
            "微信视频号解析",
            "复制 APP 内的分享链接，回到本 APP 粘贴解析即可"
        )
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "支持的 APP",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        items.forEach { item ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.name,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = buildString {
                            append(item.name)
                            append("（")
                            append(item.capability)
                            append("）")
                        },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "教程：${item.tutorial}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "进入设置项", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadPathRow(label: String, path: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HuePicker(value: Float, onValueChange: (Float) -> Unit) {
    val hues = listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
    val gradient = Brush.horizontalGradient(hues.map { Color.hsv(it, 0.82f, 0.9f) })
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(gradient)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onValueChange((it.x / size.width * 360f).coerceIn(0f, 360f)) },
                    onDrag = { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / size.width * 360f).coerceIn(0f, 360f))
                    }
                )
            }
            .semantics { contentDescription = "主题颜色取色条" }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = (value.coerceIn(0f, 360f) / 360f) * size.width
            drawCircle(Color.White, radius = 11.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
            drawCircle(Color.hsv(value, 0.68f, 0.62f), radius = 8.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height / 2))
            drawCircle(Color.Black.copy(alpha = 0.22f), radius = 12.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, size.height / 2), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    ) { Box(contentAlignment = Alignment.Center) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) } }
}

@Preview(showBackground = true)
@Composable
private fun JiquPreview() {
    JiquTheme {
        JiquApp(
            darkTheme = false,
            accentHue = 196f,
            appleFloatingNav = true,
            notificationsEnabled = true,
            autoPasteParseEnabled = true,
            parseUiState = ParseUiState(),
            onSourceUrlChange = {},
            onParse = {},
            onHistoryRecorded = {},
            onThemeChange = {},
            onAccentHueChange = {},
            onAppleFloatingNavChange = {},
            onNotificationsChange = {},
            onAutoPasteParseChange = {},
            onTestNotification = {},
            onOpenDownloadNotificationSettings = {},
            onDownload = { _, _ -> },
            onDownloadAudio = {}
        )
    }
}
