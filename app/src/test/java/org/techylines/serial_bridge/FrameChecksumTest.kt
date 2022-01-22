package org.techylines.serial_bridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.CRC32

class Frame44ChecksumTest {
    @Test
    fun empty() {
        assertEquals(0L, Frame44Checksum().value)
    }

    @Test
    fun zeros() {
        val bytes = "0000000000000000".decodeHex()
        val expect = 0L

        val checksum = Frame44Checksum()
        checksum.update(bytes)
        assertEquals(expect, checksum.value)
    }

    @Test
    fun data() {
        val bytes = "f4080eef392c1b4c".decodeHex()
        val expect = 0xC5L

        val checksum = Frame44Checksum()
        checksum.update(bytes)
        assertEquals(expect, checksum.value)
    }

    @Test
    fun frame() {
        val frame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expect = 0xC7L

        val checksum = Frame44Checksum()
        checksum.update(frame)
        assertEquals(expect, checksum.value)
    }
}

class Frame66ChecksumTest {
    @Test
    fun knownBytes() {
        // frame "66332211005800000000000000000001d59b57dc"
        val bytes = "66332211005800000000000000000000".decodeHex()
        val expect = 0xdc579bd5L

        val checksum = CRC32()
        checksum.update(bytes)
        assertEquals(expect, checksum.value)
    }

    @Test
    fun frame() {
        val frame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expect = 0x6E3F30F2L

        val checksum = Frame66Checksum()
        checksum.update(frame)
        assertEquals(expect, checksum.value)
    }
}