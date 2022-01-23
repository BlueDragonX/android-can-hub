package org.techylines.serial_bridge

import org.junit.Assert.assertEquals
import org.junit.Test

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