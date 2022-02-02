package org.techylines.can_hub.real_dash

import org.techylines.can_hub.frame.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Checksum

// Checksum implementation for RealDash 0x44 frames.
class RealDash44Checksum : Checksum {
    private var internalValue: UByte = 0u

    fun update(byte: Byte) {
        internalValue = (internalValue + byte.toUByte()).toUByte()
    }

    override fun update(int: Int) {
        internalValue = (internalValue + int.toUByte()).toUByte()
    }

    fun update(bytes: ByteArray) {
        update(bytes, 0, bytes.size)
    }

    override fun update(bytes: ByteArray, offset: Int, len: Int) {
        for (i in offset until offset+len) {
            if (i >= bytes.size) {
                break
            }
            update(bytes[i])
        }
    }

    fun update(frame: Frame) {
        update(byteArrayOf(0x44, 0x33, 0x22, 0x11))
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(frame.id.toInt())
        update(buffer.array())
        update(frame.data)
    }

    override fun getValue(): Long {
        return internalValue.toLong()
    }

    override fun reset() {
        internalValue = 0u
    }
}
