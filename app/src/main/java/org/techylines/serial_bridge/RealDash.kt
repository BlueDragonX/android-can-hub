package org.techylines.serial_bridge

import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.ParseException
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

// Read RealDash formatted frames from a byte stream.
class RealDashReader(private val byteReader: ByteReader) : FrameReader {
    private var readSize: Int = 0
    private val frameId = ByteBuffer.allocate(8)
    private var frameType: Byte = 0
    private var frameSize: Int = 0
    private var frameData = ByteBuffer.allocate(0)
    private val frameChecksum = ByteBuffer.allocate(8)
    private val calc44Checksum = RealDash44Checksum()
    private val calc66Checksum = RealDash66Checksum()

    init {
        frameId.order(ByteOrder.LITTLE_ENDIAN)
        frameChecksum.order(ByteOrder.LITTLE_ENDIAN)
    }

    override fun read(): Result<Frame> {
        for (byte in byteReader) {
            val result = update(byte)
            result.getOrNull()?.let {
                return Result.success(it)
            }
            when (val error = result.exceptionOrNull()) {
                is ParseException -> {
                    Log.d(TAG, "$error")
                }
                is Throwable -> {
                    return Result.failure(error)
                }
            }
        }
        return Result.failure(IOException("reader is closed"))
    }

    override fun close(): Throwable? {
        return byteReader.close()
    }

    override fun isClosed(): Boolean {
        return byteReader.isClosed()
    }

    // Update internal Frame state with a byte. Return a ParseException on failure, a Frame if one has been
    // built, or null if no frame is yet available.
    private fun update(byte: Byte): Result<Frame?> {
        readSize++
        when {
            readSize <= 4 -> {
                val error = updateHeader(byte)
                error?.let {
                    return Result.failure(error)
                }
            }
            readSize <= 8 -> updateId(byte)
            readSize <= frameSize + 8 -> updateData(byte)
            readSize <= frameSize + 12 -> return validateChecksum(byte)
            else -> reset()
        }
        return Result.success(null)
    }

    private fun updateHeader(byte: Byte): Throwable? {
        when (readSize) {
            // Parse header.
            1 -> {
                // We do not support RealDash text frame types.
                frameType = when (byte) {
                    0x44.b -> {
                        frameChecksum.limit(1)
                        byte
                    }
                    0x66.b -> {
                        frameChecksum.limit(4)
                        byte
                    }
                    else -> {
                        reset()
                        return ParseException("unsupported frame type, byte[0]=${byte.s}", 0)
                    }
                }
            }
            2 -> {
                if (byte != 0x33.b) {
                    reset()
                    return ParseException("invalid frame header, byte[1]=${byte.s}", 0)
                }
            }
            3 -> {
                if (byte != 0x22.b) {
                    reset()
                    return ParseException("invalid frame header, byte[2]=${byte.s}", 0)
                }
            }
            4 -> {
                if ((frameType != 0x66.b && byte != 0x11.b) || (frameType == 0x66.b && (byte < 0x11 || byte > 0x1F))) {
                    reset()
                    return Error("invalid frame header, frame type ${frameType.s} has incorrect length ${byte.s}")
                }
                val buffer = ByteBuffer.allocate(4)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(byte)
                buffer.rewind()
                frameSize = (buffer.int - 15) * 4
                frameData = ByteBuffer.allocate(frameSize)
            }
            else -> {
                return null
            }
        }
        updateChecksum(byte)
        return null
    }

    private fun updateId(byte: Byte) {
        frameId.put(byte)
        updateChecksum(byte)
    }

    private fun updateData(byte: Byte) {
        frameData.put(byte)
        updateChecksum(byte)
    }

    private fun updateChecksum(byte: Byte) {
        when (frameType) {
            0x44.b -> calc44Checksum.update(byte)
            else -> calc66Checksum.update(byte)
        }
    }

    private fun getChecksumValue(): Long {
        return when (frameType) {
            0x44.b -> calc44Checksum.value
            else -> calc66Checksum.value
        }
    }

    private fun validateChecksum(byte: Byte): Result<Frame?> {
        frameChecksum.put(byte)
        if (frameChecksum.remaining() > 0) {
            return Result.success(null)
        }

        frameChecksum.rewind()
        frameChecksum.limit(8)
        val frameChecksumValue = frameChecksum.long
        val calcChecksumValue = getChecksumValue()

        if (frameChecksumValue != calcChecksumValue) {
            reset()
            return Result.failure(IOException("frame checksum invalid, $frameChecksumValue != $calcChecksumValue"))
        }

        frameId.rewind()
        val frame = Frame(frameId.long, frameData.array())
        reset()
        return Result.success(frame)
    }

    private fun reset() {
        readSize = 0
        frameId.clear()
        frameType = 0
        frameSize = 0
        frameChecksum.clear()
        calc44Checksum.reset()
        calc66Checksum.reset()
    }
}
