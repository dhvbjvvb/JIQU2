package com.jiqu.lite

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

private const val MAX_DOWNLOAD_THREADS = 24
private const val MAX_DOWNLOAD_RANGES = 384
private const val SMALL_FILE_BYTES = 20L * 1024L * 1024L
private const val MEDIUM_FILE_BYTES = 200L * 1024L * 1024L
private const val LARGE_FILE_BYTES = 512L * 1024L * 1024L
private const val HUGE_FILE_BYTES = 2L * 1024L * 1024L * 1024L
private const val DIRECT_MEDIA_STORE_BYTES = 512L * 1024L * 1024L
private const val TARGET_RANGE_BYTES = 4L * 1024L * 1024L
private const val MAX_RANGES_PER_THREAD = 16
sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Preparing(val fileName: String) : DownloadUiState
    data class Running(
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val threadCount: Int,
        val bytesPerSecond: Long
    ) : DownloadUiState
    data class Saving(val fileName: String) : DownloadUiState
    data class Success(val fileName: String) : DownloadUiState
    data class Failure(val fileName: String, val message: String) : DownloadUiState
}

data class DownloadRequest(
    val url: String,
    val fileName: String,
    val extension: String,
    val expectedSizeBytes: Long? = null,
    val targetFolder: String = "video"
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    var uiState by mutableStateOf<DownloadUiState>(DownloadUiState.Idle)
        private set

    private var downloading = false
    private val pendingRequests = ArrayDeque<DownloadRequest>()

    fun start(request: DownloadRequest) {
        if (downloading) {
            pendingRequests.addLast(request)
            return
        }
        downloading = true
        uiState = DownloadUiState.Preparing(request.fileName)
        viewModelScope.launch {
            val speedTracker = DownloadSpeedTracker()
            try {
                MultipartDownloader(getApplication<Application>()).download(
                    request = request,
                    onSaving = {
                        withContext(Dispatchers.Main.immediate) {
                            uiState = DownloadUiState.Saving(request.fileName)
                        }
                    }
                ) { downloaded, total, threads ->
                    val bytesPerSecond = speedTracker.update(downloaded)
                    withContext(Dispatchers.Main.immediate) {
                        uiState = DownloadUiState.Running(
                            request.fileName,
                            downloaded,
                            total,
                            threads,
                            bytesPerSecond
                        )
                    }
                }
                uiState = DownloadUiState.Success(request.fileName)
                showCompletedNotification(request.fileName)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                uiState = DownloadUiState.Failure(
                    fileName = request.fileName,
                    message = error.message ?: "下载失败，请稍后重试"
                )
            } finally {
                downloading = false
                pendingRequests.removeFirstOrNull()?.let(::start)
            }
        }
    }

    fun dismissResult() {
        if (!downloading) uiState = DownloadUiState.Idle
    }

    private fun showCompletedNotification(fileName: String) {
        val application = getApplication<Application>()
        if (!application.isDownloadCompletionNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(application, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = application.getSystemService(NotificationManager::class.java)
        DownloadCompletionNotification.ensureChannel(application, manager)
        val openAppIntent = Intent(application, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            application,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(application, DownloadCompletionNotification.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("下载已完成")
            .setContentText("$fileName 已保存到 ${DownloadPaths.DISPLAY_ROOT}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        manager.notify(fileName.hashCode(), notification)
    }
}

private data class RemoteFileInfo(val totalBytes: Long, val supportsRanges: Boolean)
private data class ByteRange(val start: Long, val endInclusive: Long)

internal fun recommendedDownloadThreads(totalBytes: Long): Int = when {
    totalBytes < SMALL_FILE_BYTES -> 1
    totalBytes < MEDIUM_FILE_BYTES -> 6
    totalBytes < LARGE_FILE_BYTES -> 10
    totalBytes < HUGE_FILE_BYTES -> 16
    else -> MAX_DOWNLOAD_THREADS
}

internal class DownloadSpeedTracker(
    private val minimumSampleMillis: Long = 240L,
    private val smoothingFactor: Double = 0.35
) {
    private var lastBytes = 0L
    private var lastSampleMillis = Long.MIN_VALUE
    private var smoothedBytesPerSecond = 0.0

    fun update(
        downloadedBytes: Long,
        nowMillis: Long = System.nanoTime() / 1_000_000L
    ): Long {
        if (lastSampleMillis == Long.MIN_VALUE || downloadedBytes < lastBytes) {
            lastBytes = downloadedBytes
            lastSampleMillis = nowMillis
            smoothedBytesPerSecond = 0.0
            return 0L
        }
        val elapsedMillis = nowMillis - lastSampleMillis
        if (elapsedMillis < minimumSampleMillis) return smoothedBytesPerSecond.toLong()

        val copiedBytes = (downloadedBytes - lastBytes).coerceAtLeast(0L)
        val instantSpeed = copiedBytes * 1_000.0 / elapsedMillis.coerceAtLeast(1L)
        smoothedBytesPerSecond = if (smoothedBytesPerSecond <= 0.0) {
            instantSpeed
        } else {
            smoothedBytesPerSecond * (1.0 - smoothingFactor) + instantSpeed * smoothingFactor
        }
        lastBytes = downloadedBytes
        lastSampleMillis = nowMillis
        return smoothedBytesPerSecond.toLong().coerceAtLeast(0L)
    }
}

internal fun calculateByteRanges(
    totalBytes: Long,
    requestedParts: Int = recommendedDownloadThreads(totalBytes)
): List<LongRange> {
    require(totalBytes > 0)
    val partCount = requestedParts.coerceIn(1, MAX_DOWNLOAD_RANGES).coerceAtMost(totalBytes.toIntSafe())
    val baseSize = totalBytes / partCount
    val remainder = totalBytes % partCount
    var start = 0L
    return List(partCount) { index ->
        val size = baseSize + if (index < remainder) 1 else 0
        val range = start..(start + size - 1)
        start += size
        range
    }
}

internal fun recommendedDownloadRangeCount(totalBytes: Long, threadCount: Int): Int {
    require(totalBytes > 0)
    val normalizedThreads = threadCount.coerceIn(1, MAX_DOWNLOAD_THREADS)
    if (normalizedThreads == 1) return 1
    val sizeBasedCount = ((totalBytes + TARGET_RANGE_BYTES - 1) / TARGET_RANGE_BYTES).toIntSafe()
    return sizeBasedCount
        .coerceAtLeast(normalizedThreads)
        .coerceAtMost(normalizedThreads * MAX_RANGES_PER_THREAD)
}

internal fun shouldDownloadDirectlyToMediaStore(
    totalBytes: Long,
    supportsRanges: Boolean,
    thresholdBytes: Long = DIRECT_MEDIA_STORE_BYTES
): Boolean = supportsRanges && totalBytes >= thresholdBytes

private fun Long.toIntSafe(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal enum class DownloadStorageStrategy { STAGED, DIRECT_MEDIA_STORE }

internal class MultipartDownloader(
    private val application: Context,
    private val directMediaStoreThresholdBytes: Long = DIRECT_MEDIA_STORE_BYTES
) {
    suspend fun download(
        request: DownloadRequest,
        onSaving: suspend () -> Unit = {},
        onProgress: suspend (downloaded: Long, total: Long, threads: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val workDirectory = File(application.cacheDir, "downloads/${UUID.randomUUID()}")
        check(workDirectory.mkdirs()) { "无法创建下载缓存" }
        try {
            val remoteInfo = inspectRemoteFile(request.url, request.expectedSizeBytes)
            val totalBytes = remoteInfo.totalBytes
            // Some media CDNs advertise byte ranges but return 200 for a request
            // covering the complete file. Use the normal stream path for small
            // files so that this response is handled correctly.
            val requestedThreads = recommendedDownloadThreads(totalBytes)
            val ranges = if (remoteInfo.supportsRanges && requestedThreads > 1) {
                calculateByteRanges(
                    totalBytes = totalBytes,
                    requestedParts = recommendedDownloadRangeCount(totalBytes, requestedThreads)
                ).map { ByteRange(it.first, it.last) }
            } else {
                emptyList()
            }
            val threadCount = if (ranges.isEmpty()) 1 else requestedThreads.coerceAtMost(ranges.size)
            onProgress(0, totalBytes, threadCount)
            // Keep 100% reserved for the point after the MediaStore copy completes.
            val transferProgress: suspend (Long, Long, Int) -> Unit = { downloaded, reportedTotal, threads ->
                val effectiveTotal = reportedTotal.takeIf { it > 0 } ?: totalBytes
                if (effectiveTotal > 0) {
                    onProgress(downloaded.coerceAtMost((effectiveTotal - 1).coerceAtLeast(0L)), effectiveTotal, threads)
                } else {
                    onProgress(downloaded, reportedTotal, threads)
                }
            }

            var forceSingleStream = false
            if (shouldDownloadDirectlyToMediaStore(
                    totalBytes,
                    remoteInfo.supportsRanges,
                    directMediaStoreThresholdBytes
                )
            ) {
                try {
                    downloadPartsDirectlyToMediaStore(
                        request = request,
                        ranges = ranges,
                        threadCount = threadCount,
                        totalBytes = totalBytes,
                        onProgress = transferProgress,
                        onSaving = onSaving
                    )
                    val finalTotal = totalBytes.coerceAtLeast(1L)
                    onProgress(finalTotal, finalTotal, threadCount)
                    return@withContext DownloadStorageStrategy.DIRECT_MEDIA_STORE
                } catch (error: Throwable) {
                    if (!error.canFallbackToSingleStream()) throw error
                    // Positional writes are not supported by every MediaStore
                    // provider. Delete the pending item and retain the proven
                    // cache + single-stream path as a compatibility fallback.
                    forceSingleStream = true
                    transferProgress(0L, totalBytes, 1)
                }
            }

            val stagingFile = File(workDirectory, "complete.download")
            var finalThreadCount = threadCount
            if (ranges.isEmpty() || forceSingleStream) {
                finalThreadCount = 1
                downloadSingle(request.url, stagingFile, totalBytes, transferProgress)
            } else {
                try {
                    downloadParts(request.url, stagingFile, ranges, threadCount, totalBytes, transferProgress)
                } catch (error: Throwable) {
                    if (!error.canFallbackToSingleStream()) throw error
                    // A CDN can advertise ranges during probing but return a full
                    // 200 response for actual slices. Restart as one stream.
                    stagingFile.delete()
                    finalThreadCount = 1
                    transferProgress(0L, totalBytes, 1)
                    downloadSingle(request.url, stagingFile, totalBytes, transferProgress)
                }
            }

            onSaving()
            saveToDownloads(stagingFile, request)
            val finalTotal = totalBytes.coerceAtLeast(stagingFile.length()).coerceAtLeast(1L)
            onProgress(finalTotal, finalTotal, finalThreadCount)
            DownloadStorageStrategy.STAGED
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    private fun inspectRemoteFile(url: String, expectedSize: Long?): RemoteFileInfo {
        val connection = openConnection(url).apply { setRequestProperty("Range", "bytes=0-0") }
        return try {
            val responseCode = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range").orEmpty()
            val rangeTotal = contentRange.substringAfterLast('/', "").toLongOrNull()
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            BufferedInputStream(connection.inputStream, NETWORK_BUFFER_SIZE).use { stream ->
                val buffer = ByteArray(1)
                stream.read(buffer)
            }
            RemoteFileInfo(
                totalBytes = rangeTotal ?: expectedSize ?: contentLength.takeUnless { responseCode == HttpURLConnection.HTTP_PARTIAL }?.coerceAtLeast(0L) ?: 0L,
                supportsRanges = responseCode == HttpURLConnection.HTTP_PARTIAL && rangeTotal != null
            )
        } catch (_: IOException) {
            // Some signed media CDNs reject range probes while allowing a normal
            // stream request. Continue with the single-stream path instead of
            // failing before the actual download starts.
            RemoteFileInfo(totalBytes = expectedSize ?: 0L, supportsRanges = false)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadParts(
        url: String,
        destination: File,
        ranges: List<ByteRange>,
        threadCount: Int,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Int) -> Unit
    ) {
        RandomAccessFile(destination, "rw").use { file ->
            file.setLength(totalBytes)
            downloadParts(url, file.channel, ranges, threadCount, totalBytes, onProgress)
        }
    }

    private suspend fun downloadParts(
        url: String,
        output: FileChannel,
        ranges: List<ByteRange>,
        threadCount: Int,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Int) -> Unit
    ) = coroutineScope {
        val downloaded = AtomicLong(0)
        val lastUpdate = AtomicLong(0)
        val nextRangeIndex = AtomicInteger(0)
        List(threadCount) {
            async(Dispatchers.IO) {
                while (true) {
                    val range = ranges.getOrNull(nextRangeIndex.getAndIncrement()) ?: break
                    downloadPartWithRetry(url, output, range) { byteCount ->
                        val current = downloaded.addAndGet(byteCount)
                        emitProgress(current, totalBytes, threadCount, lastUpdate, onProgress)
                    }
                }
            }
        }.awaitAll()
        onProgress(totalBytes, totalBytes, threadCount)
    }

    private suspend fun downloadPartWithRetry(
        url: String,
        output: FileChannel,
        range: ByteRange,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        val expectedSize = range.endInclusive - range.start + 1
        var downloadedForPart = 0L
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            coroutineContext.ensureActive()
            check(downloadedForPart <= expectedSize) { "下载分片数据异常" }
            if (downloadedForPart == expectedSize) return

            val requestStart = range.start + downloadedForPart
            val connection = openConnection(url).apply {
                setRequestProperty("Range", "bytes=$requestStart-${range.endInclusive}")
            }
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    "服务器未返回分片数据"
                }
                BufferedInputStream(connection.inputStream, NETWORK_BUFFER_SIZE).use { input ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        startOffset = requestStart,
                        maxBytes = expectedSize - downloadedForPart,
                        onBytesCopied = { byteCount ->
                            downloadedForPart += byteCount
                            onBytesCopied(byteCount)
                        }
                    )
                }
                if (downloadedForPart == expectedSize) return
                lastError = IOException("下载分片不完整")
            } catch (error: Throwable) {
                coroutineContext.ensureActive()
                lastError = error
            } finally {
                connection.disconnect()
            }
            if (attempt < 2) delay(500L * (attempt + 1))
        }
        throw IOException("分片下载重试后仍然失败", lastError)
    }

    private suspend fun downloadPartsDirectlyToMediaStore(
        request: DownloadRequest,
        ranges: List<ByteRange>,
        threadCount: Int,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Int) -> Unit,
        onSaving: suspend () -> Unit
    ) {
        check(ranges.isNotEmpty()) { "远程文件不支持分片直写" }
        val resolver = application.contentResolver
        val uri = createPendingDownload(request)
        try {
            val descriptor = checkNotNull(resolver.openFileDescriptor(uri, "rw")) {
                "无法打开下载文件"
            }
            descriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).channel.use { output ->
                    prepareOutputChannel(output, totalBytes)
                    downloadParts(request.url, output, ranges, threadCount, totalBytes, onProgress)
                    output.force(true)
                }
            }
            onSaving()
            publishPendingDownload(uri)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun prepareOutputChannel(output: FileChannel, totalBytes: Long) {
        output.truncate(0L)
        if (totalBytes <= 0L) return
        val marker = ByteBuffer.allocate(1)
        while (marker.hasRemaining()) {
            check(output.write(marker, totalBytes - 1) > 0) { "无法预分配下载文件" }
        }
    }

    private fun Throwable.canFallbackToSingleStream(): Boolean =
        this !is kotlinx.coroutines.CancellationException &&
            (this is IOException || this is IllegalStateException || cause?.canFallbackToSingleStream() == true)

    private suspend fun downloadSingle(
        url: String,
        destination: File,
        knownTotal: Long,
        onProgress: suspend (Long, Long, Int) -> Unit
    ) {
        val connection = openConnection(url)
        val lastUpdate = AtomicLong(0)
        var downloaded = 0L
        try {
            check(connection.responseCode in 200..299) { "服务器返回 ${connection.responseCode}" }
            val responseLength = connection.getHeaderFieldLong("Content-Length", -1L)
            val total = responseLength.coerceAtLeast(knownTotal)
            BufferedInputStream(connection.inputStream, NETWORK_BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(destination), NETWORK_BUFFER_SIZE).use { output ->
                    copyWithProgress(
                        input = input,
                        output = output,
                        maxBytes = responseLength.takeIf { it > 0 },
                        onBytesCopied = { byteCount ->
                            downloaded += byteCount
                            emitProgress(downloaded, total, 1, lastUpdate, onProgress)
                        }
                    )
                }
            }
            onProgress(downloaded, total.coerceAtLeast(downloaded), 1)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun emitProgress(
        downloaded: Long,
        total: Long,
        threads: Int,
        lastUpdate: AtomicLong,
        onProgress: suspend (Long, Long, Int) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val previous = lastUpdate.get()
        if (now - previous >= 120 && lastUpdate.compareAndSet(previous, now)) {
            onProgress(downloaded, total, threads)
        }
    }

    private suspend fun copyWithProgress(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        maxBytes: Long? = null,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        val buffer = ByteArray(NETWORK_BUFFER_SIZE)
        var remaining = maxBytes
        while (true) {
            coroutineContext.ensureActive()
            if (remaining == 0L) break
            val requestedBytes = remaining
                ?.coerceAtMost(buffer.size.toLong())
                ?.toInt()
                ?: buffer.size
            val count = input.read(buffer, 0, requestedBytes)
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining = remaining?.minus(count)
            onBytesCopied(count.toLong())
        }
    }

    private suspend fun copyWithProgress(
        input: BufferedInputStream,
        output: FileChannel,
        startOffset: Long,
        maxBytes: Long,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        val buffer = ByteArray(NETWORK_BUFFER_SIZE)
        var remaining = maxBytes
        var position = startOffset
        while (remaining > 0L) {
            coroutineContext.ensureActive()
            val count = input.read(buffer, 0, remaining.coerceAtMost(buffer.size.toLong()).toInt())
            if (count < 0) break
            var chunk = ByteBuffer.wrap(buffer, 0, count)
            while (chunk.hasRemaining()) {
                val written = output.write(chunk, position)
                check(written > 0) { "写入下载缓存失败" }
                position += written
            }
            remaining -= count
            onBytesCopied(count.toLong())
        }
    }

    private fun saveToDownloads(source: File, request: DownloadRequest): android.net.Uri {
        val resolver = application.contentResolver
        val uri = createPendingDownload(request)
        try {
            copyToMediaStore(resolver, uri, source)
            publishPendingDownload(uri)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun createPendingDownload(request: DownloadRequest): android.net.Uri {
        val extension = request.extension.trim('.').ifBlank { "mp4" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${request.fileName}.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(extension))
            val folder = request.targetFolder
                .lowercase()
                .replace(Regex("[^a-z0-9_-]"), "")
                .ifBlank { "video" }
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/${DownloadPaths.ROOT}/$folder"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = application.contentResolver
        return checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "无法创建下载文件"
        }
    }

    private fun publishPendingDownload(uri: android.net.Uri) {
        application.contentResolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }

    /** Uses a kernel-level channel transfer when available, with a buffered fallback. */
    private fun copyToMediaStore(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri,
        source: File
    ) {
        val descriptor = checkNotNull(resolver.openFileDescriptor(uri, "w")) {
            "无法打开下载文件"
        }
        descriptor.use { pfd ->
            FileInputStream(source).use { input ->
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    val inputChannel = input.channel
                    val outputChannel = output.channel
                    val total = inputChannel.size()
                    var position = 0L
                    while (position < total) {
                        val transferred = inputChannel.transferTo(position, total - position, outputChannel)
                        if (transferred > 0L) {
                            position += transferred
                            continue
                        }

                        // Some provider/file-system combinations do not support
                        // transferTo; finish the remaining bytes with a buffered copy.
                        inputChannel.position(position)
                        outputChannel.position(position)
                        val buffer = ByteBuffer.allocate(COPY_BUFFER_SIZE)
                        while (true) {
                            buffer.clear()
                            val count = inputChannel.read(buffer)
                            if (count < 0) break
                            buffer.flip()
                            while (buffer.hasRemaining()) outputChannel.write(buffer)
                            position += count
                        }
                    }
                }
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val remoteUrl = URL(url)
        return (remoteUrl.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            applyMediaCdnHeaders(remoteUrl)
        }
    }

    /**
     * Signed Toutiao/Xigua CDN URLs validate the browser identity and source
     * page. Keep these headers on range probes and every subsequent request;
     * otherwise the probe itself is rejected with 403 before downloading starts.
     */
    private fun HttpURLConnection.applyMediaCdnHeaders(remoteUrl: URL) {
        val host = remoteUrl.host.lowercase()
        when {
            host == "bilivideo.com" || host.endsWith(".bilivideo.com") -> {
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                setRequestProperty("Referer", "https://www.bilibili.com/")
            }
            host == "ixigua.com" || host.endsWith(".ixigua.com") ||
                host == "toutiao.com" || host.endsWith(".toutiao.com") -> {
                // Xigua/Toutiao's req-auth check rejects app-specific UAs.
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://www.toutiao.com/")
                setRequestProperty("Origin", "https://www.toutiao.com")
            }
            host == "ixiaochuan.cn" || host.endsWith(".ixiaochuan.cn") -> {
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://www.pipigx.com/")
                setRequestProperty("Origin", "https://www.pipigx.com")
            }
            host == "ppxvod.com" || host.endsWith(".ppxvod.com") -> {
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://h5.pipix.com/")
                setRequestProperty("Origin", "https://h5.pipix.com")
            }
            host == "xhscdn.com" || host.endsWith(".xhscdn.com") ||
                host == "xhscdn.net" || host.endsWith(".xhscdn.net") -> {
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://www.xiaohongshu.com/")
                setRequestProperty("Origin", "https://www.xiaohongshu.com")
            }
            host == "weibocdn.com" || host.endsWith(".weibocdn.com") ||
                host == "sinaimg.cn" || host.endsWith(".sinaimg.cn") -> {
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://weibo.com/")
                setRequestProperty("Origin", "https://weibo.com")
            }
            host == "izuiyou.com" || host.endsWith(".izuiyou.com") ||
                host == "xiaochuankeji.cn" || host.endsWith(".xiaochuankeji.cn") ||
                host == "zuiyou.com" || host.endsWith(".zuiyou.com") -> {
                setRequestProperty("User-Agent", MEDIA_BROWSER_USER_AGENT)
                setRequestProperty("Referer", "https://www.izuiyou.com/")
                setRequestProperty("Origin", "https://www.izuiyou.com")
            }
            else -> setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
        }
    }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        else -> "video/mp4"
    }

    private companion object {
        const val NETWORK_BUFFER_SIZE = 512 * 1024
        const val COPY_BUFFER_SIZE = 1024 * 1024
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Android) Jiqu/1.0"
        const val MEDIA_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
