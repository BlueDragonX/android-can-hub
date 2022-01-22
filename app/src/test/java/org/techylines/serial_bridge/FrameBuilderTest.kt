package org.techylines.serial_bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FrameBuilderTest {
    @Test
    fun read44Frame() {
        val expectFrame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode44Frame(expectFrame), frames, errors)

        assertEquals(mutableListOf<Error>(), errors)
        assertEquals(mutableListOf(expectFrame), frames)
    }

    @Test
    fun read44MultipleFrames() {
        val expectFrame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expectFrame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode44Frame(expectFrame1) + RealDash.encode44Frame(expectFrame2), frames, errors)

        assertEquals(mutableListOf<Error>(), errors)
        assertEquals(mutableListOf(expectFrame1, expectFrame2), frames)
    }

    @Test
    fun read44FrameWithTrashPrefix() {
        val expectFrame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame("392c1b4c".decodeHex() + RealDash.encode44Frame(expectFrame), frames, errors)

        assertTrue(errors.size > 0)
        assertEquals(mutableListOf(expectFrame), frames)
    }

    @Test
    fun read44FrameWithTrashInTheMiddle() {
        val expectFrame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expectFrame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode44Frame(expectFrame1) + "76b2e6e7d213".decodeHex() + RealDash.encode44Frame(expectFrame2), frames, errors)

        assertTrue(errors.size > 0)
        assertEquals(mutableListOf(expectFrame1, expectFrame2), frames)
    }

    @Test
    fun read66Frame() {
        val expectFrame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode66Frame(expectFrame), frames, errors)

        assertEquals(mutableListOf<Error>(), errors)
        assertEquals(mutableListOf(expectFrame), frames)
    }

    @Test
    fun read66MultipleFrames() {
        val expectFrame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expectFrame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode66Frame(expectFrame1) + RealDash.encode66Frame(expectFrame2), frames, errors)

        assertEquals(mutableListOf<Error>(), errors)
        assertEquals(mutableListOf(expectFrame1, expectFrame2), frames)
    }

    @Test
    fun read66FrameWithTrashPrefix() {
        val expectFrame = Frame(0x5800, "f4080eef392c1b4c".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame("392c1b4c".decodeHex() + RealDash.encode66Frame(expectFrame), frames, errors)

        assertTrue(errors.size > 0)
        assertEquals(mutableListOf(expectFrame), frames)
    }

    @Test
    fun read66FrameWithTrashInTheMiddle() {
        val expectFrame1 = Frame(0x5800, "f4080eef392c1b4c".decodeHex())
        val expectFrame2 = Frame(0x5800, "4fca738ce2174708".decodeHex())

        val frames = mutableListOf<Frame>()
        val errors = mutableListOf<Error>()
        buildFrame(RealDash.encode66Frame(expectFrame1) + "76b2e6e7d213".decodeHex() + RealDash.encode66Frame(expectFrame2), frames, errors)

        assertTrue(errors.size > 0)
        assertEquals(mutableListOf(expectFrame1, expectFrame2), frames)
    }

    fun buildFrame(bytes: ByteArray, outFrames: MutableList<Frame>, outErrors: MutableList<Error>) {
        val onFrame = fun(frame: Frame) {
            outFrames.add(frame)
            println("got frame \"${frame}\"")
        }
        val onError = fun(error: Error) {
            outErrors.add(error)
            println("got error \"${error}\"")
        }

        val builder = FrameBuilder(onFrame)
        builder.update(bytes, onError)
    }

    fun checkFrames(left: MutableList<Frame>, right: MutableList<Frame>) {
        assertEquals(left.size, right.size)
        for (i in 0..left.size-1) {
            assertEquals(left[i], right[i])
        }
    }
}