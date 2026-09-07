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
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.collectLatest
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

private const val MAX_DOWNLOAD_THREADS = 8
private const val SMALL_FILE_BYTES = 20L * 1024L * 1024L
private const val LARGE_FILE_BYTES = 200L * 1024L * 1024L
sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Preparing(val fileName: String) : DownloadUiState
    data class Running(
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val threadCount: Int
    ) : DownloadUiState
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
    private val workManager = WorkManager.getInstance(application)

    fun start(request: DownloadRequest) {
        if (downloading) {
            pendingRequests.addLast(request)
            return
        }
        downloading = true
        uiState = DownloadUiState.Preparing(request.fileName)
        val input = Data.Builder()
            .putString(DownloadWorker.KEY_URL, request.url)
            .putString(DownloadWorker.KEY_FILE_NAME, request.fileName)
            .putString(DownloadWorker.KEY_EXTENSION, request.extension)
            .putLong(DownloadWorker.KEY_EXPECTED_SIZE, request.expectedSizeBytes ?: -1L)
            .putString(DownloadWorker.KEY_TARGET_FOLDER, request.targetFolder)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .build()
        workManager.enqueue(workRequest)
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { info ->
                info ?: return@collectLatest
                val progress = info.progress
                val downloaded = progress.getLong(DownloadWorker.KEY_DOWNLOADED, 0L)
                val total = progress.getLong(DownloadWorker.KEY_TOTAL, 0L)
                val threads = progress.getInt(DownloadWorker.KEY_THREADS, 1)
                if (info.state == WorkInfo.State.RUNNING) {
                    uiState = DownloadUiState.Running(request.fileName, downloaded, total, threads)
                }
                if (info.state.isFinished) {
                    // Clear progress notifications left by earlier app versions before showing completion.
                    getApplication<Application>().getSystemService(NotificationManager::class.java)
                        ?.cancel(request.fileName.hashCode())
                    if (info.state == WorkInfo.State.SUCCEEDED) {
                        uiState = DownloadUiState.Success(request.fileName)
                        showCompletedNotification(request.fileName)
                    } else {
                        uiState = DownloadUiState.Failure(
                            fileName = request.fileName,
                            message = info.outputData.getString(DownloadWorker.KEY_ERROR) ?: "下载失败，请稍后重试"
                        )
                    }
                    downloading = false
                    pendingRequests.removeFirstOrNull()?.let(::start)
                }
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
    totalBytes < LARGE_FILE_BYTES -> 4
    else -> MAX_DOWNLOAD_THREADS
}

internal fun calculateByteRanges(
    totalBytes: Long,
    requestedParts: Int = recommendedDownloadThreads(totalBytes)
): List<LongRange> {
    require(totalBytes > 0)
    val partCount = requestedParts.coerceIn(1, MAX_DOWNLOAD_THREADS).coerceAtMost(totalBytes.toIntSafe())
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

private fun Long.toIntSafe(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal class MultipartDownloader(private val application: Context) {
    suspend fun download(
        request: DownloadRequest,
        onProgress: suspend (downloaded: Long, total: Long, threads: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val workDirectory = File(application.cacheDir, "downloads/${UUID.randomUUID()}")
        check(workDirectory.mkdirs()) { "无法创建下载缓存" }
        try {
            val remoteInfo = inspectRemoteFile(request.url, request.expectedSizeBytes)
            val totalBytes = remoteInfo.totalBytes
            val ranges = if (remoteInfo.supportsRanges && totalBytes > 0) {
                calculateByteRanges(totalBytes).map { ByteRange(it.first, it.last) }
            } else {
                emptyList()
            }
            val threadCount = ranges.size.coerceAtLeast(1)
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

            val stagingFile = File(workDirectory, "complete.download")
            if (ranges.isEmpty()) {
                downloadSingle(request.url, stagingFile, totalBytes, transferProgress)
            } else {
                downloadParts(request.url, stagingFile, ranges, totalBytes, transferProgress)
            }

            saveToDownloads(stagingFile, request)
            val finalTotal = totalBytes.coerceAtLeast(stagingFile.length()).coerceAtLeast(1L)
            onProgress(finalTotal, finalTotal, threadCount)
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
            connection.inputStream.use { stream ->
                val buffer = ByteArray(1)
                stream.read(buffer)
            }
            RemoteFileInfo(
                totalBytes = rangeTotal ?: expectedSize ?: contentLength.takeUnless { responseCode == HttpURLConnection.HTTP_PARTIAL }?.coerceAtLeast(0L) ?: 0L,
                supportsRanges = responseCode == HttpURLConnection.HTTP_PARTIAL && rangeTotal != null
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadParts(
        url: String,
        destination: File,
        ranges: List<ByteRange>,
        totalBytes: Long,
        onProgress: suspend (Long, Long, Int) -> Unit
    ) = coroutineScope {
        // Each range writes directly to its final offset in the staging file.
        RandomAccessFile(destination, "rw").use { it.setLength(totalBytes) }
        val downloaded = AtomicLong(0)
        val lastUpdate = AtomicLong(0)
        ranges.map { range ->
            async(Dispatchers.IO) {
                downloadPartWithRetry(url, destination, range) { byteCount ->
                    val current = downloaded.addAndGet(byteCount)
                    emitProgress(current, totalBytes, ranges.size, lastUpdate, onProgress)
                }
            }
        }.awaitAll()
        onProgress(totalBytes, totalBytes, ranges.size)
    }

    private suspend fun downloadPartWithRetry(
        url: String,
        destination: File,
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
                BufferedInputStream(connection.inputStream).use { input ->
                    RandomAccessFile(destination, "rw").use { file ->
                        copyWithProgress(
                            input = input,
                            output = file.channel,
                            startOffset = requestStart,
                            maxBytes = expectedSize - downloadedForPart,
                            onBytesCopied = { byteCount ->
                                downloadedForPart += byteCount
                                onBytesCopied(byteCount)
                            }
                        )
                    }
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
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(destination)).use { output ->
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
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
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
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
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
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "无法创建下载文件"
        }
        try {
            copyToMediaStore(resolver, uri, source)
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
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

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Jiqu/1.0")
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
        const val COPY_BUFFER_SIZE = 1024 * 1024
    }
}
