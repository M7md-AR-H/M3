package com.m3.core.designsystem.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContrastTest {
    @Test
    fun blackOnWhiteIs21() {
        assertEquals(21.0, Contrast.ratio("#000000", "#FFFFFF")!!, 0.01)
    }

    @Test
    fun sameColorIs1() {
        assertEquals(1.0, Contrast.ratio("#A3122A", "#A3122A")!!, 0.0001)
    }

    @Test
    fun invalidHexIsNull() {
        assertNull(Contrast.parseHex("A3122A"))
        assertNull(Contrast.parseHex("#12345"))
        assertNull(Contrast.parseHex("#GGGGGG"))
        assertNull(Contrast.ratio("nope", "#FFFFFF"))
        assertNotNull(Contrast.parseHex("#a3122a"))
    }
}
