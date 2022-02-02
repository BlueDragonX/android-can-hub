package org.techylines.can_hub

import org.junit.Assert.assertEquals
import org.junit.Test
import org.techylines.can_hub.frame.FakeByteStream
import org.techylines.can_hub.frame.Frame
import java.lang.Exception
import java.util.zip.CRC32

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

class RealDashStreamTest {
    @Test
    fun read44Frame() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val byteStream = FakeByteStream(
            RealDash.encode44Frame(frame1)
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
    }

    @Test
    fun read44MultipleFrames() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val frame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())
        val byteStream = FakeByteStream(
            RealDash.encode44Frame(frame1),
            RealDash.encode44Frame(frame2)
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
        assertReadEquals(frame2, frameStream)
    }

    @Test
    fun read44FrameWithTrashPrefix() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val byteStream = FakeByteStream(
            "392c1b4c".decodeHex(),
            RealDash.encode44Frame(frame1),
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
    }

    @Test
    fun read44FrameWithTrashInTheMiddle() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val frame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())
        val byteStream = FakeByteStream(
            RealDash.encode44Frame(frame1),
            "76b2e6e7d213".decodeHex(),
            RealDash.encode44Frame(frame2),
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
        assertReadEquals(frame2, frameStream)
    }

    @Test
    fun read66MultipleFrames() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val frame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())
        val byteStream = FakeByteStream(
            RealDash.encode66Frame(frame1),
            RealDash.encode66Frame(frame2),
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
        assertReadEquals(frame2, frameStream)
    }

    @Test
    fun read66FrameWithTrashPrefix() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val bytesStream = FakeByteStream(
            "392c1b4c".decodeHex(),
            RealDash.encode66Frame(frame1),
        )

        val frameStream = RealDashStream(bytesStream)
        assertReadEquals(frame1, frameStream)
    }

    @Test
    fun read66FrameWithTrashInTheMiddle() {
        val frame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val frame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())
        val byteStream = FakeByteStream(
            RealDash.encode66Frame(frame1),
            "76b2e6e7d213".decodeHex(),
            RealDash.encode66Frame(frame2),
        )

        val frameStream = RealDashStream(byteStream)
        assertReadEquals(frame1, frameStream)
        assertReadEquals(frame2, frameStream)
    }

    @Test
    fun writeFrames() {
        val frames = listOf(
            Frame(0x5800, "f4080eef392c1b4c".decodeHex()),
            Frame(0x5800, "4fca738ce2174708".decodeHex()),
        )
        assertWrite(frames)
    }

    @Test
    fun writeFrameInParts() {
        val frames = listOf(
            Frame(0x5800, "f4080eef392c1b4c".decodeHex()),
            Frame(0x5800, "4fca738ce2174708".decodeHex()),
        )
        assertWrite(frames, 2)
    }

    private fun assertReadEquals(expect: Any, stream: RealDashStream) {
        val result = stream.read()
        val expectResult: Result<Frame> = when(expect) {
            is Frame -> Result.success(expect)
            is Throwable -> Result.failure(expect)
            else -> throw Exception("unexpected assertReadEquals expect type, must be Frame or Throwable")
        }
        assertEquals(expectResult, result)
    }

    private fun assertWrite(frames: List<Frame>, writeLimit: Int = 0) {
        val expect = frames.map { RealDash.encode66Frame(it) }.reduce { A, B -> A+B }

        val byteStream = FakeByteStream(4096, writeLimit)
        val frameStream = RealDashStream(byteStream)
        for (frame in frames) {
            assertEquals(null, frameStream.write(frame))
        }
        assertEquals(expect.toHexString(), byteStream.buffer.toHexString())
    }
}