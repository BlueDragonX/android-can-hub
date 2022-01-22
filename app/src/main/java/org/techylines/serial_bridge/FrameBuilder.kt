package org.techylines.serial_bridge

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Build a frame one byte at a time. Frames are sent to the callback when ready.
class FrameBuilder(val onFrame: (Frame)->Unit) {
    private var readSize: Int = 0
    private val frameId = ByteBuffer.allocate(8)
    private var frameType: Byte = 0
    private var frameSize: Int = 0
    private var frameData = ByteBuffer.allocate(0)
    private val frameChecksum = ByteBuffer.allocate(8)
    private val calc44Checksum = Frame44Checksum()
    private val calc66Checksum = Frame66Checksum()

    init {
        frameId.order(ByteOrder.LITTLE_ENDIAN)
        frameChecksum.order(ByteOrder.LITTLE_ENDIAN)
    }

    // Update the builder with a byte. Return an error if parsing fails.
    fun update(byte: Byte): Error? {
        readSize++
        when {
            readSize <= 4 -> return updateHeader(byte)
            readSize <= 8 -> updateId(byte)
            readSize <= frameSize + 8 -> updateData(byte)
            readSize <= frameSize + 12 -> return validateChecksum(byte)
            else -> reset()
        }
        return null
    }

    // Update the builder with an array of bytes. Call onError() if an error occurs.
    fun update(bytes: ByteArray, onError: (Error)->Unit) {
        for (byte in bytes) {
            update(byte)?.let {
                onError(it)
            }
        }
    }

    private fun updateHeader(byte: Byte): Error? {
        when (readSize) {
            // Parse header.
            1 -> {
                // We only support std and ext frame types.
                when (byte) {
                    0x44.b -> {
                        frameChecksum.limit(1)
                        frameType = byte
                    }
                    0x66.b -> {
                        frameChecksum.limit(4)
                        frameType = byte
                    }
                    else -> {
                        reset()
                        return Error("unsupported frame type, byte[0]=${byte.s}")
                    }
                }
            }
            2 -> {
                if (byte != 0x33.b) {
                    reset()
                    return Error("invalid frame header, byte[1]=${byte.s}")
                }
            }
            3 -> {
                if (byte != 0x22.b) {
                    reset()
                    return Error("invalid frame header, byte[2]=${byte.s}")
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
                frameSize = (buffer.getInt() - 15) * 4;
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

    private fun validateChecksum(byte: Byte): Error? {
        frameChecksum.put(byte)
        if (frameChecksum.remaining() > 0) {
            return null;
        }

        frameChecksum.rewind()
        frameChecksum.limit(8)
        val frameChecksumValue = frameChecksum.getLong()

        var error: Error? = null
        if (frameChecksumValue == getChecksumValue()) {
            frameId.rewind()
            onFrame(Frame(frameId.getLong(), frameData.array()))
        }
        else {
            frameChecksum.rewind()
            error = Error("frame checksum invalid, ${frameChecksumValue} != ${getChecksumValue()}")
        }

        reset()
        return error
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

    val Int.b: Byte get() = toByte()
    val Byte.s: String get() = toString(16)
}
