package com.jiqu.lite

import android.provider.MediaStore
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiqu.lite.data.parseMediaUrl
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun parsesDouyinThroughDedicatedGateway() = runBlocking {
        val media = parseMediaUrl("https://v.douyin.com/xFvcY5x4HAI/").getOrThrow()

        assertEquals("douyin", media.platform)
        assertTrue(media.downloadUrl.startsWith("https://"))
        assertTrue(
            "Expected multiple download qualities, got ${media.downloadOptions.size}",
            media.downloadOptions.size > 1
        )
        assertEquals("原画", media.resolution)
        val qualities = media.downloadOptions.map { it.quality }
        assertEquals(qualities.distinct(), qualities)
        assertEquals("原画", qualities.first())
        assertTrue(qualities.drop(1).all { it.matches(Regex("\\d{3,4}p")) })
        assertTrue((media.bitRate ?: 0) > 0)
    }

    @Test
    fun parsesBugPkDouyinWithoutLegacyCredentials() = runBlocking {
        val media = parseMediaUrl("https://v.douyin.com/eEEfBw-3-pQ/").getOrThrow()

        assertEquals("douyin", media.platform)
        assertTrue(media.downloadUrl.startsWith("http"))
        assertTrue(media.downloadOptions.isNotEmpty())
    }

    @Test
    fun parsesWechatChannelsThroughDedicatedGateway() = runBlocking {
        val media = parseMediaUrl("https://weixin.qq.com/sph/AoPX5bEBDd").getOrThrow()

        assertEquals("微信视频号", media.platform)
        assertEquals("视频", media.mediaType)
        assertTrue(media.title.isNotBlank())
        assertTrue(media.downloadUrl.startsWith("https://finder.video.qq.com/"))
        assertTrue(media.downloadOptions.isNotEmpty())
        assertTrue((media.sizeBytes ?: 0) > 0)
        assertTrue(media.downloadOptions.all { (it.sizeBytes ?: 0) > 0 })
    }

    @Test
    fun downloadsWechatChannelsMediaToMediaStore() {
        runBlocking {
            val media = parseMediaUrl("https://weixin.qq.com/sph/AXXRx9senB").getOrThrow()
            val fileName = "android_test_wechat_${System.currentTimeMillis()}"
            val progress = mutableListOf<Long>()
            val context = InstrumentationRegistry.getInstrumentation().targetContext

            MultipartDownloader(context).download(
                DownloadRequest(
                    url = media.downloadUrl,
                    fileName = fileName,
                    extension = media.fileExtension,
                    expectedSizeBytes = media.sizeBytes
                )
            ) { downloaded, _, _ ->
                progress += downloaded
            }

            assertTrue("download progress was never reported", progress.isNotEmpty())
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf("$fileName.${media.fileExtension}"),
                null
            )?.use { cursor ->
                assertTrue("downloaded file was not written to MediaStore", cursor.moveToFirst())
            }
        }
    }

    @Test
    fun parsesKuaishouThroughDedicatedGateway() = runBlocking {
        val media = parseMediaUrl("https://v.kuaishou.com/KAjGG5zb").getOrThrow()

        assertEquals("快手", media.platform)
        assertEquals("实况", media.mediaType)
        assertTrue(media.title.isNotBlank())
        assertTrue(media.downloadUrl.startsWith("http://") || media.downloadUrl.startsWith("https://"))
        assertTrue(media.downloadUrl.contains(".mp4", ignoreCase = true))
        assertEquals("实况", media.downloadOptions.single().quality)
        assertTrue(media.audioUrl?.endsWith(".m4a") == true)
        assertTrue((media.sizeBytes ?: 0) > 0)
        assertTrue(media.downloadOptions.all { (it.sizeBytes ?: 0) > 0 })
    }

    @Test
    fun parseSurvivesRecreationAndBottomNavigation() {
        composeRule.onNode(hasSetTextAction())
            .performTextInput("https://v.douyin.com/XBwlKFr1ya0/")
        composeRule.onNodeWithText("开始解析").performClick()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("下载媒体").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription("预览加载中")
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("解析").performClick()
        composeRule.onNodeWithText("下载媒体").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("预览加载中").assertCountEquals(0)

        composeRule.onNodeWithText("下载媒体").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("选择下载清晰度").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("选择下载清晰度").assertIsDisplayed()
    }

    @Test
    fun settingsTutorialsListLegacyPlatforms() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("解析教程与支持").performClick()

        listOf(
            "皮皮虾（皮皮虾无水印视频）",
            "小红书（无水印解析小红书视频和图文）",
            "最右（无水印解析最右视频）",
            "今日头条（无水印解析今日头条短视频）"
        ).forEach { title ->
            composeRule.onNodeWithText(title).assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("教程：复制 App 内的分享链接，回到本 APP 粘贴解析即可")
            .assertCountEquals(9)
    }
}
