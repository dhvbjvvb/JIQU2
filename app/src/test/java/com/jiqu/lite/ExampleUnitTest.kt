package com.jiqu.lite

import com.jiqu.lite.data.isDouyinUrl
import com.jiqu.lite.data.isKuaishouUrl
import com.jiqu.lite.data.isWechatChannelsUrl
import org.junit.Test

import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun douyinHostsAreAccepted() {
        assertTrue(isDouyinUrl("https://v.douyin.com/XBwlKFr1ya0/"))
        assertTrue(isDouyinUrl("https://www.douyin.com/video/123"))
        assertTrue(isDouyinUrl("https://www.iesdouyin.com/share/video/123"))
    }

    @Test
    fun nonDouyinHostsAreRejected() {
        assertFalse(isDouyinUrl("https://example.com/?url=https://v.douyin.com/test"))
        assertFalse(isDouyinUrl("https://douyin.com.example.com/video/123"))
        assertFalse(isDouyinUrl("not-a-url"))
    }

    @Test
    fun wechatChannelsHostsAreAccepted() {
        assertTrue(isWechatChannelsUrl("https://channels.weixin.qq.com/web/pages/feed?finderFeedId=123"))
        assertTrue(isWechatChannelsUrl("https://weixin.qq.com/s/abc123"))
        assertTrue(isWechatChannelsUrl("https://v.weixin.qq.com/share/video/123"))
    }

    @Test
    fun unrelatedHostsAreRejectedForWechatChannels() {
        assertFalse(isWechatChannelsUrl("https://example.com/?url=https://channels.weixin.qq.com/test"))
        assertFalse(isWechatChannelsUrl("https://weixin.qq.com.example.com/s/abc"))
        assertFalse(isWechatChannelsUrl("not-a-url"))
    }

    @Test
    fun kuaishouHostsAreAccepted() {
        assertTrue(isKuaishouUrl("https://v.kuaishou.com/KAjGG5zb"))
        assertTrue(isKuaishouUrl("https://www.kuaishou.com/short-video/123"))
        assertTrue(isKuaishouUrl("https://www.kwai.com/short-video/123"))
    }

    @Test
    fun unrelatedHostsAreRejectedForKuaishou() {
        assertFalse(isKuaishouUrl("https://example.com/?url=https://v.kuaishou.com/test"))
        assertFalse(isKuaishouUrl("https://kuaishou.com.example.com/video/123"))
        assertFalse(isKuaishouUrl("not-a-url"))
    }
}
