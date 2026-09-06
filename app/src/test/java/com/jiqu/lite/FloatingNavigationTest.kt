package com.jiqu.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingNavigationTest {
    @Test
    fun mapsDragPositionToBottomDestination() {
        assertEquals(0, destinationIndexForPosition(0f, 300f, 3))
        assertEquals(0, destinationIndexForPosition(99f, 300f, 3))
        assertEquals(1, destinationIndexForPosition(100f, 300f, 3))
        assertEquals(2, destinationIndexForPosition(299f, 300f, 3))
        assertEquals(2, destinationIndexForPosition(400f, 300f, 3))
    }
}
