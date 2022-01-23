package org.techylines.serial_bridge

import java.io.IOException
import java.nio.ByteBuffer

// Reads from a ByteArray. Closes when it reaches the end of the array.
class FakeByteReader(val buffer: ByteArray, override val readBufferSize: Int = 4096) : ByteReader {
    private var position: Int = 0
    private val iter = ByteReaderIterator(this)

    constructor(vararg byteArrays: ByteArray, rdBufferSize: Int = 4096) : this(byteArrays.reduce { A, B -> A+B }, rdBufferSize)

    @Synchronized
    override fun read(bytes: ByteArray): Result<Int> {
        if (isClosed()) {
            return Result.failure(IOException("reader is closed"))
        }
        val remaining = buffer.size - position
        val readSize = if (remaining > bytes.size) bytes.size else remaining
        for (i in 0 until readSize) {
            bytes[i] = buffer[position]
            position++
        }
        return Result.success(readSize)
    }

    override fun close(): Throwable? {
        position = buffer.size
        return null
    }

    override fun isClosed(): Boolean {
        return position >= buffer.size
    }

    override fun iterator(): Iterator<Byte> {
        return iter
    }
}

// Reads and writes to an internal buffer. Does not close until calling close.
class FakeByteStream(val buffer: MutableList<Byte>, override val readBufferSize: Int = 4096) : ByteStream {
    private var position: Int = 0
    private var closed = false
    private val iter = ByteReaderIterator(this)


    val bytes: ByteArray
        @Synchronized get() {
            val byteArray = ByteArray(buffer.size)
            for (i  in 0 until buffer.size) {
                byteArray[i] = buffer[i]
            }
            return byteArray
        }

    constructor(readBufferSize: Int = 4096) : this(mutableListOf(), readBufferSize)
    constructor(bytes: ByteArray, readBufferSize: Int = 4096) : this(bytes.toMutableList(), readBufferSize)
    constructor(bytes: ByteBuffer, readBufferSize: Int = 4096) : this(bytes.array().toMutableList(), readBufferSize)

    @Synchronized
    override fun read(bytes: ByteArray): Result<Int> {
        if (isClosed()) {
            return Result.failure(IOException("reader is closed"))
        }
        val remaining = buffer.size - position
        if (remaining == 0) {
            return Result.failure(IOException("buffer overrun"))
        }
        val readSize = if (remaining > bytes.size) bytes.size else remaining
        for (i in 0 until readSize) {
            bytes[i] = buffer[position]
            position++
        }
        return Result.success(readSize)
    }

    @Synchronized
    override fun write(bytes: ByteArray): Result<Int> {
        if (isClosed()) {
            return Result.failure(IOException("writer is closed"))
        }
        for (byte in bytes) {
            buffer.add(byte)
        }
        return Result.success(bytes.size)
    }

    @Synchronized
    override fun close(): Throwable? {
        closed = true
        return null
    }

    @Synchronized
    override fun isClosed(): Boolean {
        return closed
    }

    override fun iterator(): Iterator<Byte> {
        return iter
    }

    @Synchronized
    fun rewind() {
        position = 0
        closed = false
    }

    @Synchronized
    fun reset() {
        rewind()
        buffer.clear()
    }
}

class FakeFrameReaderWriter(val frames: MutableList<Frame>) : FrameStream {
    private var closed = false
    private var position = 0

    @Synchronized
    override fun read(): Result<Frame?> {
        if (isClosed()) {
            return Result.failure(IOException("reader is closed"))
        }
        if (position >= frames.size) {
            return Result.failure(IOException("buffer overrun"))
        }
        position++
        return Result.success(frames[position-1])
    }

    @Synchronized
    override fun write(frame: Frame): Throwable? {
        if (isClosed()) {
            return IOException("writer is closed")
        }
        frames.add(frame)
        return null
    }

    @Synchronized
    override fun close(): Throwable? {
        closed = true
        return null
    }

    @Synchronized
    override fun isClosed(): Boolean {
        return closed
    }

    @Synchronized
    fun rewind() {
        position = 0
        closed = false
    }
}