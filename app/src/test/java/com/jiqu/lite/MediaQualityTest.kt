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
        assertTrue(isSupportedMediaUrl("https://h5.pipix.com/s/example/"))
        assertTrue(isSupportedMediaUrl("https://xhslink.com/a/example"))
        assertTrue(isSupportedMediaUrl("https://share.xiaochuankeji.cn/hybrid/share/post?pid=1"))
        assertTrue(isSupportedMediaUrl("https://m.toutiao.com/is/example/"))
        assertTrue(isSupportedMediaUrl("https://www.doubao.com/chat/abc"))
        assertTrue(isSupportedMediaUrl("https://jimeng.jianying.com/ai-tool/video"))
        assertTrue(isSupportedMediaUrl("https://www.pipigx.com/post/abc"))
    }

    @Test
    fun unrelatedHostsAreIgnoredByAutoParse() {
        assertFalse(isSupportedMediaUrl("https://example.com/video.mp4"))
        assertFalse(isSupportedMediaUrl("https://notdoubao.com/video"))
        assertFalse(isSupportedMediaUrl("https://xiaohongshu.com.example.com/post/1"))
        assertFalse(isSupportedMediaUrl("https://qishui.douyin.com/s/example/"))
    }
}
