package org.techylines.serial_bridge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Checksum

object RealDash {
    fun encode44Frame(frame: Frame): ByteArray {
        val cs = RealDash44Checksum()
        cs.update(frame)
        val buffer = ByteBuffer.allocate(9 + frame.data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("44332211".decodeHex())
        buffer.putInt(frame.id.toInt())
        buffer.put(frame.data)
        buffer.put(cs.value.toByte())
        return buffer.array()
    }

    fun encode66Frame(frame: Frame): ByteArray {
        val cs = RealDash66Checksum()
        cs.update(frame)
        val buffer = ByteBuffer.allocate(12 + frame.data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("663322".decodeHex())
        buffer.put((frame.data.size / 4 + 15).toByte())
        buffer.putInt(frame.id.toInt())
        buffer.put(frame.data)
        buffer.putInt(cs.value.toInt())
        return buffer.array()
    }

}

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
        for (i in offset..offset+len-1) {
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
