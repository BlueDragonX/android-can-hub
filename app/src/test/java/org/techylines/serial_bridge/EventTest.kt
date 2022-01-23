package org.techylines.serial_bridge

import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class StreamNodeTest {
    @Test
    fun receiveSingleFrame() {
        assertStream(listOf(
            FrameBroadcast("test", Frame(0x5800, "f4080eef392c1b4c".decodeHex())),
        ))
    }

    @Test
    fun receiveMultipleFrame() {
        assertStream(listOf(
            FrameBroadcast("test", Frame(0x5800, "f4080eef392c1b4c".decodeHex())),
            FrameBroadcast("test", Frame(0x5800, "4fca738ce2174708".decodeHex())),
        ))
    }

    private fun assertStream(expect: List<FrameBroadcast>) {
        val lock = ReentrantLock()
        val condition = lock.newCondition()
        val actual = mutableListOf<FrameBroadcast>()

        val node = StreamNode("test", FakeFrameReaderWriter(expect.map {it.frame}.toMutableList()))
        node.listen {
            lock.withLock {
                actual.add(it)
                if (actual.size == expect.size) {
                    condition.signal()
                }
            }
        }

        lock.withLock {
            condition.await(60, TimeUnit.SECONDS)
        }
        node.join()
        node.close()

        assertEquals(expect, actual)
    }
}