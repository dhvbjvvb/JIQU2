package com.jiqu.lite

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

internal enum class UpdateSource(val displayName: String) {
    GitHub("GitHub")
}

internal data class AppUpdate(
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val source: UpdateSource
)

internal sealed interface UpdateUiState {
    data object Hidden : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data class SelectingChannel(val update: AppUpdate) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
    data class Downloading(
        val update: AppUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateUiState
    data class AwaitingInstallPermission(val update: AppUpdate) : UpdateUiState
    data class DownloadFailed(val update: AppUpdate, val message: String) : UpdateUiState
}

internal fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
    val remoteParts = remoteVersion.toVersionParts() ?: return false
    val currentParts = currentVersion.toVersionParts() ?: return false
    val partCount = maxOf(remoteParts.size, currentParts.size)
    repeat(partCount) { index ->
        val remotePart = remoteParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (remotePart != currentPart) return remotePart > currentPart
    }
    return false
}

internal fun isTrustedUpdateUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    if (!uri.scheme.equals("https", ignoreCase = true)) return@runCatching false
    val host = uri.host?.lowercase() ?: return@runCatching false
    val path = uri.path.lowercase()
    when (host) {
        "github.com" -> path.startsWith("/dhvbjvvb/jiqu2/releases/download/")
        "gh-proxy.com", "ghfast.top" -> path.startsWith("/https://github.com/dhvbjvvb/jiqu2/releases/download/")
        else -> false
    }
}.getOrDefault(false)

private fun String.toVersionParts(): List<Int>? {
    val normalized = trim().removePrefix("v").removePrefix("V")
    if (normalized.isBlank()) return null
    return normalized.split('.').map { part ->
        part.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    }
}

internal fun isApkReleaseAssetName(name: String): Boolean =
    name.substringBefore('?').endsWith(".apk", ignoreCase = true)

private sealed interface SourceResult {
    data class Release(val update: AppUpdate) : SourceResult
    data object NoRelease : SourceResult
    data object Failed : SourceResult
}

internal class ReleaseUpdateClient {
    fun checkForUpdate(currentVersion: String): Result<AppUpdate?> {
        val github = GITHUB_API_URLS.asSequence()
            .map { checkApiSource(it, UpdateSource.GitHub) }
            .firstOrNull { it is SourceResult.Release }
            ?: checkGitHubWeb()
        val releases = if (github is SourceResult.Release) listOf(github.update) else emptyList()
        if (releases.isEmpty()) return Result.failure(IllegalStateException("没有可用的更新源"))

        val update = releases
            .filter { isRemoteVersionNewer(it.versionName, currentVersion) }
            .maxWithOrNull { left, right -> compareVersions(left.versionName, right.versionName) }
        return Result.success(update)
    }

    private fun checkApiSource(url: String, source: UpdateSource): SourceResult = runCatching {
        val response = request(url, followRedirects = true, acceptJson = true)
        if (response.code == HttpURLConnection.HTTP_NOT_FOUND) return SourceResult.NoRelease
        if (response.code !in 200..299) return SourceResult.Failed
        parseRelease(JSONObject(response.body), source) ?: SourceResult.Failed
    }.getOrDefault(SourceResult.Failed)

    private fun parseRelease(root: JSONObject, source: UpdateSource): SourceResult? {
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return SourceResult.NoRelease
        val version = root.optString("tag_name").trim()
        if (version.toVersionParts() == null) return null
        val asset = root.optJSONArray("assets")?.findApkAsset() ?: return null
        val downloadUrl = sequenceOf("browser_download_url", "download_url", "url")
            .map(asset::optString)
            .firstOrNull(::isTrustedUpdateUrl)
            ?: return null
        return SourceResult.Release(
            AppUpdate(
                versionName = version.removePrefix("v").removePrefix("V"),
                releaseNotes = root.optString("body").trim().ifBlank { "本次版本包含功能优化与问题修复。" },
                downloadUrl = downloadUrl,
                assetName = asset.optString("name").ifBlank { "JIQU-$version.apk" },
                assetSizeBytes = asset.optLong("size").coerceAtLeast(0L),
                source = source
            )
        )
    }

    private fun checkGitHubWeb(): SourceResult = runCatching {
        val latest = GITHUB_LATEST_URLS.asSequence()
            .map { request(it, followRedirects = false) }
            .firstOrNull { it.code in 300..399 && !it.location.isNullOrBlank() }
            ?: return SourceResult.Failed
        val location = latest.location ?: return SourceResult.Failed
        val tag = URI(location).path.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return SourceResult.Failed
        if (tag.toVersionParts() == null) return SourceResult.Failed

        val assetsPage = GITHUB_ASSET_PAGE_BASES.asSequence()
            .map { request("$it$tag", followRedirects = true) }
            .firstOrNull { it.code in 200..299 }
            ?: return SourceResult.Failed
        if (assetsPage.code !in 200..299) return SourceResult.Failed
        val path = APK_LINK_PATTERN.findAll(assetsPage.body)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .firstOrNull()
            ?: return SourceResult.Failed
        val downloadUrl = if (path.startsWith("https://")) path else "https://github.com$path"
        if (!isTrustedUpdateUrl(downloadUrl)) return SourceResult.Failed

        SourceResult.Release(
            AppUpdate(
                versionName = tag.removePrefix("v").removePrefix("V"),
                releaseNotes = readGitHubReleaseNotes(tag),
                downloadUrl = downloadUrl,
                assetName = URI(downloadUrl).path.substringAfterLast('/'),
                assetSizeBytes = 0L,
                source = UpdateSource.GitHub
            )
        )
    }.getOrDefault(SourceResult.Failed)

    private fun readGitHubReleaseNotes(tag: String): String = runCatching {
        val apiNotes = GITHUB_API_TAG_BASES.asSequence()
            .map { request("$it$tag", followRedirects = true, acceptJson = true) }
            .firstOrNull { it.code in 200..299 }
            ?.let { JSONObject(it.body).optString("body").trim() }
            ?.takeIf { it.isNotBlank() }
        if (!apiNotes.isNullOrBlank()) return apiNotes
        val page = request("https://github.com/dhvbjvvb/JIQU2/releases/tag/$tag", followRedirects = true)
        val html = RELEASE_NOTES_PATTERN.find(page.body)?.groupValues?.get(1).orEmpty()
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            .ifBlank { "请查看 $tag 版本的发布说明。" }
    }.getOrDefault("请查看 $tag 版本的发布说明。")

    private fun JSONArray.findApkAsset(): JSONObject? =
        (0 until length()).asSequence()
            .mapNotNull(::optJSONObject)
            .filter { isApkReleaseAssetName(it.optString("name")) }
            .sortedByDescending { it.optString("name").contains("signed", ignoreCase = true) }
            .firstOrNull()

    private fun compareVersions(left: String, right: String): Int {
        if (isRemoteVersionNewer(left, right)) return 1
        if (isRemoteVersionNewer(right, left)) return -1
        return 0
    }

    private fun request(
        url: String,
        followRedirects: Boolean,
        acceptJson: Boolean = false
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = followRedirects
            setRequestProperty("User-Agent", "JIQU-Android/${BuildConfig.VERSION_NAME}")
            if (acceptJson) setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(
                code = code,
                body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
                location = connection.getHeaderField("Location")
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val code: Int, val body: String, val location: String?)

    private companion object {
        // Prefer public GitHub CDN mirrors for users whose networks cannot reach GitHub reliably.
        val GITHUB_API_URLS = listOf(
            "https://gh-proxy.com/https://api.github.com/repos/dhvbjvvb/JIQU2/releases/latest",
            "https://ghfast.top/https://api.github.com/repos/dhvbjvvb/JIQU2/releases/latest",
            "https://api.github.com/repos/dhvbjvvb/JIQU2/releases/latest"
        )
        val GITHUB_API_TAG_BASES = listOf(
            "https://gh-proxy.com/https://api.github.com/repos/dhvbjvvb/JIQU2/releases/tags/",
            "https://ghfast.top/https://api.github.com/repos/dhvbjvvb/JIQU2/releases/tags/",
            "https://api.github.com/repos/dhvbjvvb/JIQU2/releases/tags/"
        )
        val GITHUB_LATEST_URLS = listOf(
            "https://gh-proxy.com/https://github.com/dhvbjvvb/JIQU2/releases/latest",
            "https://ghfast.top/https://github.com/dhvbjvvb/JIQU2/releases/latest",
            "https://github.com/dhvbjvvb/JIQU2/releases/latest"
        )
        val GITHUB_ASSET_PAGE_BASES = listOf(
            "https://gh-proxy.com/https://github.com/dhvbjvvb/JIQU2/releases/expanded_assets/",
            "https://ghfast.top/https://github.com/dhvbjvvb/JIQU2/releases/expanded_assets/",
            "https://github.com/dhvbjvvb/JIQU2/releases/expanded_assets/"
        )
        val APK_LINK_PATTERN = Regex(
            """href=[\"']([^\"']+/releases/download/[^\"']+\.apk(?:\?[^\"']*)?)[\"']""",
            RegexOption.IGNORE_CASE
        )
        val RELEASE_NOTES_PATTERN = Regex(
            """<div[^>]*class=[\"'][^\"']*markdown-body[^\"']*[\"'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}

internal class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE)
    private val updateClient = ReleaseUpdateClient()
    private var pendingInstallFile: File? = null
    private var pendingInstallUpdate: AppUpdate? = null
    private var checking = false
    private var showCurrentCheckResult = false

    var uiState by mutableStateOf<UpdateUiState>(UpdateUiState.Hidden)
        private set

    fun checkForUpdate(manual: Boolean) {
        if (uiState is UpdateUiState.Downloading) return
        if (!manual && pendingInstallFile != null) return
        if (!manual && preferences.getBoolean(AppPreferences.AUTOMATIC_UPDATE_CHECK_IGNORED, false)) return
        if (checking) {
            if (manual) {
                showCurrentCheckResult = true
                uiState = UpdateUiState.Checking
            }
            return
        }
        checking = true
        showCurrentCheckResult = manual
        if (manual) uiState = UpdateUiState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                updateClient.checkForUpdate(BuildConfig.VERSION_NAME)
            }
            checking = false
            val showResult = showCurrentCheckResult
            showCurrentCheckResult = false
            uiState = result.fold(
                onSuccess = { update ->
                    when {
                        update != null -> UpdateUiState.Available(update)
                        showResult -> UpdateUiState.UpToDate
                        else -> UpdateUiState.Hidden
                    }
                },
                onFailure = {
                    if (showResult) UpdateUiState.Failed("检查更新失败，请检查网络后重试")
                    else UpdateUiState.Hidden
                }
            )
        }
    }

    fun ignoreAutomaticChecks() {
        preferences.edit().putBoolean(AppPreferences.AUTOMATIC_UPDATE_CHECK_IGNORED, true).apply()
        uiState = UpdateUiState.Hidden
    }

    fun dismiss() {
        if (uiState !is UpdateUiState.Downloading) uiState = UpdateUiState.Hidden
    }

    fun downloadAndInstall(context: Context, update: AppUpdate) {
        if (uiState is UpdateUiState.Downloading) return
        uiState = UpdateUiState.Downloading(update, 0L, update.assetSizeBytes)
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    downloadUpdate(update) { downloaded, total ->
                        viewModelScope.launch {
                            uiState = UpdateUiState.Downloading(update, downloaded, total)
                        }
                    }
                }
                withContext(Dispatchers.IO) { validateDownloadedApk(file) }
                pendingInstallFile = file
                pendingInstallUpdate = update
                requestInstall(context)
            }.onFailure { error ->
                pendingInstallFile?.delete()
                pendingInstallFile = null
                pendingInstallUpdate = null
                uiState = UpdateUiState.DownloadFailed(
                    update,
                    error.message?.takeIf { it.isNotBlank() } ?: "下载或校验更新包失败"
                )
            }
        }
    }

    fun chooseDownloadChannel(update: AppUpdate) {
        if (uiState !is UpdateUiState.Downloading) uiState = UpdateUiState.SelectingChannel(update)
    }

    fun selectUpdateChannel(context: Context, update: AppUpdate, useGitHub: Boolean) {
        if (useGitHub) {
            downloadAndInstall(context, update)
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("蓝奏云提取码", LANZOU_PASSWORD))
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(LANZOU_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        uiState = UpdateUiState.Hidden
    }

    fun resumePendingInstall(context: Context) {
        if (pendingInstallFile?.isFile == true && context.packageManager.canRequestPackageInstalls()) {
            requestInstall(context)
        }
    }

    fun continuePendingInstall(context: Context) {
        if (pendingInstallFile?.isFile == true) requestInstall(context)
    }

    private fun requestInstall(context: Context) {
        val file = pendingInstallFile?.takeIf(File::isFile) ?: return
        val update = pendingInstallUpdate ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            uiState = UpdateUiState.AwaitingInstallPermission(update)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        uiState = UpdateUiState.Hidden
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
        pendingInstallFile = null
        pendingInstallUpdate = null
    }

    private fun downloadUpdate(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): File {
        check(isTrustedUpdateUrl(update.downloadUrl)) { "更新下载地址不可信" }
        val updateDirectory = File(getApplication<Application>().cacheDir, "updates")
        check(updateDirectory.exists() || updateDirectory.mkdirs()) { "无法创建更新缓存" }
        updateDirectory.listFiles()?.forEach(File::delete)
        val target = File(updateDirectory, "JIQU-${update.versionName}.apk")
        val candidates = downloadCandidates(update.downloadUrl)
        var lastError: Throwable? = null
        for (candidate in candidates) {
            val connection = (URL(candidate).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 90_000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "JIQU-Android/${BuildConfig.VERSION_NAME}")
            }
            try {
                check(connection.responseCode in 200..299) { "服务器返回 ${connection.responseCode}" }
                val total = connection.getHeaderFieldLong("Content-Length", update.assetSizeBytes)
                    .coerceAtLeast(update.assetSizeBytes)
                var downloaded = 0L
                var lastUpdateAt = 0L
                BufferedInputStream(connection.inputStream).use { input ->
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            val now = System.currentTimeMillis()
                            if (now - lastUpdateAt >= 120L) {
                                lastUpdateAt = now
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                check(target.length() > 0L) { "更新包为空" }
                onProgress(target.length(), total.coerceAtLeast(target.length()))
                return target
            } catch (error: Throwable) {
                lastError = error
                target.delete()
            } finally {
                connection.disconnect()
            }
        }
        throw lastError ?: IllegalStateException("无法下载更新包")
    }

    private fun downloadCandidates(original: String): List<String> {
        val mirrors = listOf(
            "https://gh-proxy.com/",
            "https://ghfast.top/"
        )
        return (mirrors.map { it + original } + original).distinct()
            .filter(::isTrustedUpdateUrl)
    }

    private fun validateDownloadedApk(file: File) {
        val application = getApplication<Application>()
        val packageManager = application.packageManager
        val current = packageManager.getInstalledPackageInfo(application.packageName)
        val archive = packageManager.getArchivePackageInfo(file)
            ?: error("无法读取更新包")
        check(archive.packageName == application.packageName) { "更新包的应用标识不一致" }
        check(archive.longVersionCode > current.longVersionCode) { "更新包版本不高于当前版本" }
        check(current.signerDigests().intersect(archive.signerDigests()).isNotEmpty()) {
            "更新包签名与当前应用不一致"
        }
    }

    private fun PackageManager.getInstalledPackageInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun PackageManager.getArchivePackageInfo(file: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    private fun PackageInfo.signerDigests(): Set<String> {
        val info = signingInfo ?: return emptySet()
        val signatures = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        return signatures.map(Signature::toByteArray).mapTo(mutableSetOf()) { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val LANZOU_URL = "https://wwbjl.lanzout.com/b01d77wdje"
        const val LANZOU_PASSWORD = "ccvd"
    }
}
