package com.jiqu.lite

import com.jiqu.lite.data.isSupportedMediaUrl
import com.jiqu.lite.data.MediaDownloadOption
import com.jiqu.lite.data.normalizeResolvedMediaUrl
import com.jiqu.lite.data.endpointRequiresApiKey
import com.jiqu.lite.data.MediaPlatform
import com.jiqu.lite.data.parseEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQualityTest {
    @Test
    fun supportedPlatformHostsAreRecognizedForAutoParse() {
        assertTrue(isSupportedMediaUrl("https://v.douyin.com/example/"))
        assertTrue(isSupportedMediaUrl("https://b23.tv/example"))
        assertTrue(isSupportedMediaUrl("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertTrue(isSupportedMediaUrl("https://v.kuaishou.com/example"))
        assertTrue(isSupportedMediaUrl("https://v.weixin.qq.com/share/video/1"))
        assertTrue(isSupportedMediaUrl("https://h5.pipix.com/s/example/"))
        assertTrue(isSupportedMediaUrl("https://xhslink.com/a/example"))
        assertTrue(isSupportedMediaUrl("https://xhslink.cn/o/example"))
        assertTrue(isSupportedMediaUrl("https://share.xiaochuankeji.cn/hybrid/share/post?pid=1"))
        assertTrue(isSupportedMediaUrl("https://m.toutiao.com/is/example/"))
        assertTrue(isSupportedMediaUrl("https://www.doubao.com/chat/abc"))
        assertTrue(isSupportedMediaUrl("https://jimeng.jianying.com/ai-tool/video"))
        assertTrue(isSupportedMediaUrl("https://www.pipigx.com/post/abc"))
        assertTrue(isSupportedMediaUrl("https://h5.pipigx.cn/pp/post/abc"))
        assertTrue(isSupportedMediaUrl("https://www.xiaohongshu.net/explore/abc"))
        assertTrue(isSupportedMediaUrl("https://share.zuiyou.com/hybrid/share/post?pid=1"))
    }

    @Test
    fun unrecognizedHttpHostsUseUniversalGateway() {
        assertTrue(isSupportedMediaUrl("https://example.com/video.mp4"))
        assertTrue(isSupportedMediaUrl("https://notdoubao.com/video"))
        assertTrue(isSupportedMediaUrl("https://xiaohongshu.com.example.com/post/1"))
        assertTrue(isSupportedMediaUrl("https://qishui.douyin.com/s/example/"))
        assertFalse(isSupportedMediaUrl("not-a-url"))
    }

    @Test
    fun pipigxCleartextCdnUrlIsUpgradedToHttps() {
        assertEquals(
            "https://video01.szsttkj.com/path/video?id=1",
            normalizeResolvedMediaUrl("http://video01.szsttkj.com/path/video?id=1")
        )
        assertEquals(
            "http://example.com/path/video",
            normalizeResolvedMediaUrl("http://example.com/path/video")
        )
    }

    @Test
    fun originalQualityKeepsItsPixelResolutionForDownloadDetails() {
        val option = MediaDownloadOption(
            quality = "原画",
            width = 2160,
            height = 3840,
            bitRate = 12_000_000,
            sizeBytes = 100_000_000,
            downloadUrl = "https://example.com/video.mp4",
            fileExtension = "mp4"
        )

        assertEquals("原画", option.resolution)
        assertEquals("2160 × 3840", option.pixelResolution)
    }

    @Test
    fun apiGatewayKeyRulesAreHostBased() {
        assertFalse(endpointRequiresApiKey("https://api.bugpk.com/api/pipigx"))
        assertTrue(endpointRequiresApiKey("https://api-new.ifphp.com/api/pipigx"))
        assertTrue(endpointRequiresApiKey("https://api-new.ifphp.com/api/svparse"))
        assertFalse(endpointRequiresApiKey("https://example.com/api/pipigx"))
    }

    @Test
    fun bugPkOnlyPlatformsDoNotUseUniversalIfphpFallback() {
        listOf(
            MediaPlatform.PIPIXIA,
            MediaPlatform.ZUIYOU,
            MediaPlatform.TOUTIAO
        ).forEach { platform ->
            val endpoints = parseEndpoints(platform)
            assertTrue(endpoints.isNotEmpty())
            assertTrue(endpoints.all { it.startsWith("https://api.bugpk.com/api/") })
        }
    }

    @Test
    fun ifphpPlatformsKeepKeyedGateway() {
        listOf(
            MediaPlatform.BILIBILI,
            MediaPlatform.WECHAT_CHANNELS,
            MediaPlatform.DOUBAO,
            MediaPlatform.JIMENG
        ).forEach { platform ->
            assertTrue(parseEndpoints(platform).all(::endpointRequiresApiKey))
        }
    }

    @Test
    fun douyinAndKuaishouUseLegacyGatewayBeforeUniversalFallback() {
        listOf(
            MediaPlatform.DOUYIN to "https://api-new.ifphp.com/api/dyjx",
            MediaPlatform.KUAISHOU to "https://api-new.ifphp.com/api/ksjx"
        ).forEach { (platform, legacyEndpoint) ->
            val endpoints = parseEndpoints(platform)
            assertEquals(2, endpoints.size)
            assertEquals(legacyEndpoint, endpoints[0])
            assertEquals("https://mxper.cc.cd/api/v1/parse", endpoints[1])
            assertTrue(endpointRequiresApiKey(endpoints[0]))
            assertFalse(endpointRequiresApiKey(endpoints[1]))
        }
    }

    @Test
    fun xiaohongshuUsesOnlyUniversalGateway() {
        val endpoints = parseEndpoints(MediaPlatform.XIAOHONGSHU)
        assertEquals(1, endpoints.size)
        assertEquals("https://mxper.cc.cd/api/v1/parse", endpoints.single())
        assertFalse(endpointRequiresApiKey(endpoints.single()))
        assertFalse(endpoints.single().contains("ifphp.com"))
    }

    @Test
    fun unrecognizedPlatformsUseUniversalGateway() {
        val endpoints = parseEndpoints(MediaPlatform.OTHER)
        assertEquals(listOf("https://mxper.cc.cd/api/v1/parse"), endpoints)
        assertFalse(endpointRequiresApiKey(endpoints.single()))
    }
}
