package com.jiqu.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun comparesSemanticVersions() {
        assertTrue(isRemoteVersionNewer("v2.0.3", "2.0.2"))
        assertTrue(isRemoteVersionNewer("2.1", "2.0.9"))
        assertFalse(isRemoteVersionNewer("2.0.2", "2.0.2"))
        assertFalse(isRemoteVersionNewer("1.9.9", "2.0.2"))
        assertFalse(isRemoteVersionNewer("invalid", "2.0.2"))
    }

    @Test
    fun acceptsOnlyExpectedReleaseHostsAndPaths() {
        assertTrue(isTrustedUpdateUrl("https://github.com/dhvbjvvb/JIQU2/releases/download/v2.0.3/app.apk"))
        assertTrue(isTrustedUpdateUrl("https://gitee.com/DIOT486/JIQU2/attach_files/123/download/app.apk"))
        assertFalse(isTrustedUpdateUrl("http://github.com/dhvbjvvb/JIQU2/releases/download/v2.0.3/app.apk"))
        assertFalse(isTrustedUpdateUrl("https://example.com/app.apk"))
        assertFalse(isTrustedUpdateUrl("https://github.com/another/repo/releases/download/v2/app.apk"))
    }
}
