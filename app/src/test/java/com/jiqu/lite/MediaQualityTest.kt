package com.jiqu.lite

import com.jiqu.lite.data.isSupportedMediaUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQualityTest {
    @Test
    fun supportedPlatformHostsAreRecognizedForAutoParse() {
        assertTrue(isSupportedMediaUrl("https://v.douyin.com/example/"))
        assertTrue(isSupportedMediaUrl("https://v.kuaishou.com/example"))
        assertTrue(isSupportedMediaUrl("https://v.weixin.qq.com/share/video/1"))
    }

    @Test
    fun unrelatedHostsAreIgnoredByAutoParse() {
        assertFalse(isSupportedMediaUrl("https://example.com/video.mp4"))
    }
}
