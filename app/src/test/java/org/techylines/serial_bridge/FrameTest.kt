package org.techylines.serial_bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FrameTest {
    @Test
    fun constructor_empty() {
        val frame = Frame()
        assertEquals(0, frame.id)
        assertEquals(0, frame.data.size)
    }

    @Test
    fun constructor_id() {
        val id = 0x520L
        val frame = Frame(id)
        assertEquals(id, frame.id)
        assertEquals(0, frame.data.size)
    }

    @Test
    fun constructor_id_bytes() {
        val id = 0x540L
        val bytes = "f4080eef392c1b4c".decodeHex()

        val frame = Frame(id, bytes)
        assertEquals(id, frame.id)
        assertEquals(bytes, frame.data)
    }

    @Test
    fun equals_empty_is_equal() {
    }

    @Test
    fun equals_is_equal() {
        val id = 0x540L
        val bytes = "f4080eef392c1b4c".decodeHex()

        val left = Frame(id, bytes)
        val right = Frame(id, bytes)
        assertEquals(left, right)
    }

    @Test
    fun equals_id_not_equal() {
        val bytes = "f4080eef392c1b4c".decodeHex()

        val left = Frame(0x540, bytes)
        val right = Frame(0x625, bytes)
        assertNotEquals(left, right)
    }

    @Test
    fun equals_data_not_equal() {
        val id = 0x625L

        val left = Frame(id, "f4080eef392c1b4c".decodeHex())
        val right = Frame(id, "0000000000000000".decodeHex())
        assertNotEquals(left, right)
    }

    @Test
    fun equals_nothing_equal() {
        val left = Frame(0x5600, "f4080eef392c1b4c".decodeHex())
        val right = Frame(0x5800, "0000000000000000".decodeHex())
        assertNotEquals(left, right)
    }
}