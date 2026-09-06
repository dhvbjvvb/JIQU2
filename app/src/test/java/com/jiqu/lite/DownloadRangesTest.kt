package com.jiqu.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRangesTest {
    @Test
    fun createsSixtyFourContinuousRangesForNormalFile() {
        val ranges = calculateByteRanges(10_003)

        assertEquals(64, ranges.size)
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

        assertEquals(7, ranges.size)
        assertTrue(ranges.all { it.first == it.last })
    }

    @Test
    fun requestedThreadCountIsCappedAtSixtyFour() {
        assertEquals(64, calculateByteRanges(100_000, requestedParts = 128).size)
    }
}
