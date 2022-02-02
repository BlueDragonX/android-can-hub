package org.techylines.can_hub.frame

import org.junit.Assert.assertEquals
import org.junit.Test
import org.techylines.can_hub.decodeHex
import org.techylines.can_hub.toHexString

class ByteReaderIteratorTest {
    @Test
    fun readUntilClosed() {
        val expect = "f4080eef392c1b4c"
        val reader = FakeByteReader(expect.decodeHex(), 2)
        val actual = mutableListOf<Byte>()

        for (byte in reader) {
            actual.add(byte)
        }
        assertEquals(expect, actual.toHexString())
    }
}