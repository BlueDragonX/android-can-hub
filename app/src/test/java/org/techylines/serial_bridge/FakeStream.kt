package org.techylines.serial_bridge

import java.io.IOException

// Reads from a ByteArray. Closes when it reaches the end of the array.
class FakeByteReader(val buffer: ByteArray, override val readBufferSize: Int = 4096) : ByteReader {
    private var position: Int = 0
    private val iter = ByteReaderIterator(this)

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
class FakeByteStream(val buffer: MutableList<Byte>, override val readBufferSize: Int = 4096, private val writeLimit: Int = 0) : ByteStream {
    private var position: Int = 0
    private var closed = false
    private val iter = ByteReaderIterator(this)

    constructor(readBufferSize: Int = 4096, writeLimit: Int = 0) : this(mutableListOf(), readBufferSize, writeLimit)
    constructor(bytes: ByteArray, readBufferSize: Int = 4096) : this(bytes.toMutableList(), readBufferSize)
    constructor(vararg byteArrays: ByteArray, readBufferSize: Int = 4096) : this(byteArrays.reduce { A, B -> A+B }, readBufferSize)

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
        if (writeLimit <= 0 || bytes.size < writeLimit) {
            for (byte in bytes) {
                buffer.add(byte)
            }
            return Result.success(bytes.size)
        } else {
            for (i in 0 until writeLimit) {
                buffer.add(bytes[i])
            }
            return Result.success(writeLimit)
        }
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
}