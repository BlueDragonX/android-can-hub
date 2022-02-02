package org.techylines.can_hub.frame

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.techylines.can_hub.decodeHex

class FrameBusTest {
    @Test
    fun sendReceiveClose() = runBlocking {
        val frames = listOf(
            Frame(0x5200, "f4080eef392c1b4c".decodeHex()),
            Frame(0x5400, "4fca738ce2174708".decodeHex()),
        )

        val stream1 = ChannelFrameStream()
        val stream2 = ChannelFrameStream()
        for (frame in frames) {
            stream1.readChannel.send(frame)
        }

        val bus = FrameBus(this)
        bus.add(stream1)
        bus.add(stream2)
        var actual = receiveN(stream2.writeChannel, 2)
        bus.close()

        assertEquals(frames, actual)
        assertTrue(stream1.isClosed())
        assertTrue(stream2.isClosed())
    }

    @Test
    fun sendAcrossStreams() = runBlocking {
        val frame1 = Frame(0x5200, "f4080eef392c1b4c".decodeHex())
        val frame2 = Frame(0x5400, "4fca738ce2174708".decodeHex())

        val stream1 = ChannelFrameStream()
        val stream2 = ChannelFrameStream()
        stream1.readChannel.send(frame1)
        stream2.readChannel.send(frame2)

        val bus = FrameBus(this)
        bus.add(stream1)
        bus.add(stream2)
        var actual1 = receiveN(stream1.writeChannel, 1)
        var actual2 = receiveN(stream2.writeChannel, 1)
        bus.close()

        assertEquals(listOf(frame1), actual2)
        assertEquals(listOf(frame2), actual1)
        assertTrue(stream1.isClosed())
        assertTrue(stream2.isClosed())
    }

    @Test
    fun closeStream() = runBlocking {
        val frames = listOf(
            Frame(0x5200, "f4080eef392c1b4c".decodeHex()),
            Frame(0x5400, "4fca738ce2174708".decodeHex()),
        )

        val stream1 = ChannelFrameStream()
        val stream2 = FakeFrameStream()

        val bus = FrameBus(this)
        bus.add(stream1)
        bus.add(stream2)
        stream2.close()
        for (frame in frames) {
            stream1.readChannel.send(frame)
        }
        bus.close()

        assertEquals(listOf<Frame>(), stream2.written)
    }

    @Test
    fun fanOut() = runBlocking {
        val frames = listOf(
            Frame(0x5200, "f4080eef392c1b4c".decodeHex()),
            Frame(0x5400, "4fca738ce2174708".decodeHex()),
        )

        val stream1 = ChannelFrameStream()
        val stream2 = ChannelFrameStream()
        val stream3 = ChannelFrameStream()
        val stream4 = ChannelFrameStream()

        val bus = FrameBus(this)

        bus.add(stream1)
        bus.add(stream2)
        bus.add(stream3)
        bus.add(stream4)
        yield()

        for (frame in frames) {
            stream1.readChannel.send(frame)
        }
        yield()

        val actual2 = receiveN(stream2.writeChannel, 2)
        val actual3 = receiveN(stream3.writeChannel, 2)
        val actual4 = receiveN(stream4.writeChannel, 2)
        bus.close()

        assertEquals(frames, actual2)
        assertEquals(frames, actual3)
        assertEquals(frames, actual4)
        assertTrue(stream1.isClosed())
        assertTrue(stream2.isClosed())
        assertTrue(stream3.isClosed())
        assertTrue(stream4.isClosed())
    }

    @Test
    fun multiFanOut() = runBlocking {
        var frame1 = Frame(0x5200, "f4080eef392c1b4c".decodeHex())
        var frame2 = Frame(0x5400, "4fca738ce2174708".decodeHex())
        var frame3 = Frame(0x5600, "955bde2c16d8998f".decodeHex())

        val stream1 = ChannelFrameStream()
        val stream2 = ChannelFrameStream()
        val stream3 = ChannelFrameStream()
        val stream4 = ChannelFrameStream()

        val bus = FrameBus(this)
        bus.add(stream1)
        bus.add(stream2)
        bus.add(stream3)
        bus.add(stream4)
        yield()

        stream1.readChannel.send(frame1)
        stream2.readChannel.send(frame2)
        stream3.readChannel.send(frame3)
        yield()

        val actual1 = receiveN(stream1.writeChannel, 2)
        val actual2 = receiveN(stream2.writeChannel, 2)
        val actual3 = receiveN(stream3.writeChannel, 2)
        val actual4 = receiveN(stream4.writeChannel, 3)
        bus.close()

        assertEquals(listOf(frame2, frame3), actual1)
        assertEquals(listOf(frame1, frame3), actual2)
        assertEquals(listOf(frame1, frame2), actual3)
        assertEquals(listOf(frame1, frame2, frame3), actual4)
        assertTrue(stream1.isClosed())
        assertTrue(stream2.isClosed())
        assertTrue(stream3.isClosed())
        assertTrue(stream4.isClosed())
    }

    suspend fun <T> receiveN(ch: Channel<T>, n: Int): List<T> {
        val items = mutableListOf<T>()
        for (i in 0 until n) {
            items.add(ch.receive())
        }
        return items
    }

    fun <T> tryReceiveN(ch: Channel<T>, n: Int): List<T> {
        val items = mutableListOf<T>()
        for (i in 0 until n) {
            var result = ch.tryReceive()
            result.getOrNull()?.let {
                items.add(it)
            }
        }
        return items
    }
}