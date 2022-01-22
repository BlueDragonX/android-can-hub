package org.techylines.serial_bridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.CRC32
import kotlin.math.exp

class RealDashTest {
    @Test
    fun encode44Frame() {
        val frame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expect = "4433221100580000f4080eef392c1b4cc7"
        assertEquals(expect, RealDash.encode44Frame(frame).toHexString())
    }

    @Test
    fun encode66Frame_8bytes() {
        val frame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expect = "6633221100580000f4080eef392c1b4cf2303f6e"
        assertEquals(expect, RealDash.encode66Frame(frame).toHexString())
    }

    @Test
    fun encode66Frame_64bytes() {
        val frame = Frame(0x5200, "e91cfe5aa17a184ee152ff9a47e827114ff446f15fcadd133f76276703a9551c9b8e83e9ff74de5244a77564863dcc2a912828be79f6af50fa6d31ea9d8b5bd2".decodeHex())
        val expect = "6633221f00520000e91cfe5aa17a184ee152ff9a47e827114ff446f15fcadd133f76276703a9551c9b8e83e9ff74de5244a77564863dcc2a912828be79f6af50fa6d31ea9d8b5bd28498390d"
        assertEquals(expect, RealDash.encode66Frame(frame).toHexString())
    }
}

class RealDash44ChecksumTest {
    @Test
    fun empty() {
        assertEquals(0L, RealDash44Checksum().value)
    }

    @Test
    fun zeros() {
        val bytes = "0000000000000000".decodeHex()
        val expect = 0L

        val checksum = RealDash44Checksum()
        checksum.update(bytes)
        assertEquals(expect, checksum.value)
    }

    @Test
    fun data() {
        val bytes = "f4080eef392c1b4c".decodeHex()
        val expect = 0xC5L

        val checksum = RealDash44Checksum()
        checksum.update(bytes)
        assertEquals(expect, checksum.value)
    }

    @Test
    fun frame() {
        val frame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expect = 0xC7L

        val checksum = RealDash44Checksum()
        checksum.update(frame)
        assertEquals(expect, checksum.value)
    }
}

class RealDash66ChecksumTest {
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

        val checksum = RealDash66Checksum()
        checksum.update(frame)
        assertEquals(expect, checksum.value)
    }
}