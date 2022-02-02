package org.techylines.can_hub.real_dash

import android.util.Log
import org.techylines.can_hub.*
import org.techylines.can_hub.frame.ByteStream
import org.techylines.can_hub.frame.Frame
import org.techylines.can_hub.frame.FrameStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Read RealDash formatted frames from a byte stream.
open class RealDashStream(private val byteStream: ByteStream) : FrameStream {
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
        for (byte in byteStream) {
            val result = update(byte)
            result.getOrNull()?.let {
                return Result.success(it)
            }
            when (val error = result.exceptionOrNull()) {
                is ParseError -> {
                    Log.d(TAG, "$error")
                }
                is Throwable -> {
                    return Result.failure(error)
                }
            }
        }
        return Result.failure(IOException("reader is closed"))
    }

    override fun write(frame: Frame): Error? {
        var position = 0
        val bytes = RealDash.encode66Frame(frame)
        while (position < bytes.size) {
            val result = byteStream.write(bytes.sliceArray(IntRange(position, bytes.size-1)))
            val written = result.getOrNull()
            position += written ?: return result.errorOrNull()
        }
        return null
    }

    override fun close(): Error? {
        return byteStream.close()
    }

    override fun isClosed(): Boolean {
        return byteStream.isClosed()
    }

    // Update internal Frame state with a byte. Return a ParseError on failure, a Frame if one has been
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

    private fun updateHeader(byte: Byte): Error? {
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
                        return ParseError("unsupported frame type, byte[0]=${byte.s}")
                    }
                }
            }
            2 -> {
                if (byte != 0x33.b) {
                    reset()
                    return ParseError("invalid frame header, byte[1]=${byte.s}")
                }
            }
            3 -> {
                if (byte != 0x22.b) {
                    reset()
                    return ParseError("invalid frame header, byte[2]=${byte.s}")
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