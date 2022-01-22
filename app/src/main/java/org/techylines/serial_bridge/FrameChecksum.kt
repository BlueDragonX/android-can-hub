package org.techylines.serial_bridge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Checksum

// Checksum implementation for 0x44 frames.
class Frame44Checksum : Checksum {
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

// Checksum implementation for 0x66 frames.
class Frame66Checksum : CRC32() {
    fun update(byte: Byte) {
        update(byte.toInt())
    }

    fun update(frame: Frame) {
        val header = byteArrayOf(0x66, 0x33, 0x22, (frame.data.size / 4 + 15).toByte())
        update(byteArrayOf(0x66, 0x33, 0x22, (frame.data.size / 4 + 15).toByte()))
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(frame.id.toInt())
        update(buffer.array())
        update(frame.data)
    }
}