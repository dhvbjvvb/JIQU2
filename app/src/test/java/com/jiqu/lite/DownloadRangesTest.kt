package com.jiqu.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadRangesTest {
    @Test
    fun createsSingleRangeForSmallFile() {
        val ranges = calculateByteRanges(10_003)

        assertEquals(1, ranges.size)
        assertEquals(0L, ranges.first().first)
        assertEquals(10_002L, ranges.last().last)
        ranges.zipWithNext().forEach { (current, next) ->
            assertEquals(current.last + 1, next.first)
        }
        assertEquals(10_003L, ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun smallFileDoesNotCreateEmptyRanges() {
        val ranges = calculateByteRanges(7)

        assertEquals(1, ranges.size)
        assertEquals(0L..6L, ranges.single())
    }

    @Test
    fun requestedThreadCountIsCappedAtEight() {
        assertEquals(8, calculateByteRanges(300L * 1024L * 1024L, requestedParts = 128).size)
    }

    @Test
    fun choosesThreadsByFileSize() {
        assertEquals(1, recommendedDownloadThreads(19L * 1024L * 1024L))
        assertEquals(4, recommendedDownloadThreads(20L * 1024L * 1024L))
        assertEquals(8, recommendedDownloadThreads(200L * 1024L * 1024L))
    }
}
