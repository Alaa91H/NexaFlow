package com.nexaflow.build

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitVersionTest {

    @Test
    fun patchValuesAboveNineRemainDistinct() {
        val patch9 = encodeVersionCode(3, 58, 9)
        val patch10 = encodeVersionCode(3, 58, 10)
        val patch99 = encodeVersionCode(3, 58, 99)

        assertTrue(patch9 < patch10)
        assertTrue(patch10 < patch99)
    }

    @Test
    fun semanticOrderingIsMonotonicAcrossPatchMinorAndMajor() {
        assertTrue(encodeVersionCode(3, 58, 999) < encodeVersionCode(3, 59, 0))
        assertTrue(encodeVersionCode(3, 999, 999) < encodeVersionCode(4, 0, 0))
    }

    @Test
    fun commitDistanceUsesTheReservedTwoDigitSuffix() {
        assertEquals(
            encodeVersionCode(3, 90, 0) + 42,
            encodeVersionCode(3, 90, 0, 42)
        )
        assertTrue(
            encodeVersionCode(3, 90, 0, 99) <
                encodeVersionCode(3, 90, 1, 0)
        )
    }

    @Test
    fun maximumSupportedSemanticVersionFitsAndroidLimit() {
        assertEquals(2_099_999_999, encodeVersionCode(20, 999, 999, 99))
    }

    @Test
    fun unsupportedRangesFailInsteadOfSilentlyColliding() {
        assertFailsWith<IllegalArgumentException> { encodeVersionCode(21, 0, 0) }
        assertFailsWith<IllegalArgumentException> { encodeVersionCode(3, 1000, 0) }
        assertFailsWith<IllegalArgumentException> { encodeVersionCode(3, 90, 1000) }
        assertFailsWith<IllegalArgumentException> { encodeVersionCode(3, 90, 0, 100) }
    }
}
