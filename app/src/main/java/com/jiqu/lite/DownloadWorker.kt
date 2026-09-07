package com.jiqu.lite

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException

internal class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val request = DownloadRequest(
            url = inputData.getString(KEY_URL).orEmpty(),
            fileName = inputData.getString(KEY_FILE_NAME).orEmpty(),
            extension = inputData.getString(KEY_EXTENSION).orEmpty(),
            expectedSizeBytes = inputData.getLong(KEY_EXPECTED_SIZE, -1L).takeIf { it > 0 },
            targetFolder = inputData.getString(KEY_TARGET_FOLDER) ?: DownloadPaths.VIDEO
        )
        if (request.url.isBlank() || request.fileName.isBlank()) return Result.failure()
        return runCatching {
            MultipartDownloader(applicationContext).download(request) { downloaded, total, threads ->
                setProgress(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total, KEY_THREADS to threads))
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (runAttemptCount < MAX_RETRY_ATTEMPTS && error.isTransientDownloadError()) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf(KEY_ERROR to (error.message ?: "下载失败，请稍后重试")))
                }
            }
        )
    }

    private fun Throwable.isTransientDownloadError(): Boolean =
        this is IOException || (this is IllegalStateException &&
            message.orEmpty().contains(Regex("服务器返回 (408|429|5\\d{2})")))

    companion object {
        const val TAG = "jiqu_download"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_EXTENSION = "extension"
        const val KEY_EXPECTED_SIZE = "expected_size"
        const val KEY_TARGET_FOLDER = "target_folder"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_THREADS = "threads"
        const val KEY_ERROR = "error"
        const val MAX_RETRY_ATTEMPTS = 3
    }
}
