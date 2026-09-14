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
    fun requestedRangeCountUsesIndependentCap() {
        assertEquals(128, calculateByteRanges(300L * 1024L * 1024L, requestedParts = 128).size)
        assertEquals(384, calculateByteRanges(2L * 1024L * 1024L * 1024L, requestedParts = 512).size)
    }

    @Test
    fun choosesThreadsByFileSize() {
        assertEquals(1, recommendedDownloadThreads(19L * 1024L * 1024L))
        assertEquals(6, recommendedDownloadThreads(20L * 1024L * 1024L))
        assertEquals(10, recommendedDownloadThreads(200L * 1024L * 1024L))
        assertEquals(16, recommendedDownloadThreads(512L * 1024L * 1024L))
        assertEquals(24, recommendedDownloadThreads(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun createsMoreRangesThanWorkersToReduceSlowTail() {
        val totalBytes = 90L * 1024L * 1024L
        val threadCount = recommendedDownloadThreads(totalBytes)
        val rangeCount = recommendedDownloadRangeCount(totalBytes, threadCount)
        val ranges = calculateByteRanges(totalBytes, rangeCount)

        assertEquals(6, threadCount)
        assertEquals(23, rangeCount)
        assertEquals(rangeCount, ranges.size)
        assertEquals(totalBytes, ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun rangeCountStaysSingleForSingleThreadDownloads() {
        assertEquals(1, recommendedDownloadRangeCount(10L * 1024L * 1024L, 1))
    }

    @Test
    fun twoGigabyteDownloadUsesFineGrainedWorkQueue() {
        val totalBytes = 2L * 1024L * 1024L * 1024L
        val threadCount = recommendedDownloadThreads(totalBytes)
        val rangeCount = recommendedDownloadRangeCount(totalBytes, threadCount)
        val ranges = calculateByteRanges(totalBytes, rangeCount)

        assertEquals(24, threadCount)
        assertEquals(384, rangeCount)
        assertEquals(rangeCount, ranges.size)
        assertEquals(totalBytes, ranges.sumOf { it.last - it.first + 1 })
    }

    @Test
    fun onlyLargeRangeDownloadsWriteDirectlyToMediaStore() {
        assertEquals(false, shouldDownloadDirectlyToMediaStore(511L * 1024L * 1024L, true))
        assertEquals(false, shouldDownloadDirectlyToMediaStore(2L * 1024L * 1024L * 1024L, false))
        assertEquals(true, shouldDownloadDirectlyToMediaStore(2L * 1024L * 1024L * 1024L, true))
    }

    @Test
    fun calculatesSmoothedDownloadSpeedAndResetsAfterRestart() {
        val tracker = DownloadSpeedTracker(minimumSampleMillis = 200L, smoothingFactor = 0.5)

        assertEquals(0L, tracker.update(0L, nowMillis = 1_000L))
        assertEquals(1_024_000L, tracker.update(204_800L, nowMillis = 1_200L))
        assertEquals(768_000L, tracker.update(307_200L, nowMillis = 1_400L))
        assertEquals(0L, tracker.update(0L, nowMillis = 1_500L))
    }
}
