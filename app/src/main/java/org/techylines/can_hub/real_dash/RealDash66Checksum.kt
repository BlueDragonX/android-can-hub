package org.techylines.can_hub.real_dash

import org.techylines.can_hub.frame.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

// Checksum implementation for RealDash 0x66 frames.
class RealDash66Checksum : CRC32() {
    fun update(byte: Byte) {
        update(byte.toInt())
    }

    fun update(frame: Frame) {
        update(byteArrayOf(0x66, 0x33, 0x22, (frame.data.size / 4 + 15).toByte()))
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(frame.id.toInt())
        update(buffer.array())
        update(frame.data)
    }
}