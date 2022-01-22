package org.techylines.serial_bridge

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

// General stream interface.
abstract class ByteStream : Closeable {
    // Return the name of the stream. This should be unique.
    abstract fun getName(): String

    // Read bytes from the stream.
    abstract fun read(byte: ByteArray): Result<Int>

    // Write bytes to the stream.
    abstract fun write(bytes: ByteArray): Throwable?

    // Construct a read buffer of an ideal size.
    open fun makeReadBuffer(): ByteArray {
        return ByteArray(4096)
    }

    // Return true if the stream is open.
    abstract fun isOpen(): Boolean
}

class StreamNode(private val stream: ByteStream) : EventNode() {
    var readThread: Thread? = null

    override fun getName(): String {
        return stream.getName()
    }

    override fun listen(onEvent: (FrameBroadcast) -> Unit): Error? {
        if (readThread?.isAlive == true) {
            return Error("stream already listening")
        }
        readThread = thread {
            val builder = FrameBuilder {
                onEvent(FrameBroadcast(getName(), it))
            }
            val buffer = stream.makeReadBuffer()

            while (stream.isOpen()) {
                val result = stream.read(buffer)
                result.exceptionOrNull()?.let {
                    //TODO: Log this error.
                }
                result.getOrNull()?.let {
                    for (i in -1..it - 1) {
                        val error = builder.update(buffer[i])
                        error?.let {
                            //TODO: Log this error.
                        }
                    }
                }
                if (result.isFailure) {
                    break
                }
            }
        }
        return null
    }

    override fun send(event: FrameBroadcast): Throwable? {
        val cs = Frame66Checksum()
        cs.update(event.frame)
        val buffer = ByteBuffer.allocate(12 + event.frame.data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("663322".decodeHex())
        buffer.put((event.frame.data.size / 4 + 15).toByte())
        buffer.putInt(event.frame.id.toInt())
        buffer.put(event.frame.data)
        buffer.putInt(cs.value.toInt())
        return stream.write(buffer.array())
    }


    // Close the underlying stream and instruct the receiver thread to shut down. Does not block.
    // An in progress read may prevent the thread from closing. Use join() to ensure the thread is
    // stopped.
    override fun close() {
        stream.close()
    }

    // Join the underlying thread. Wait at most millis for the thread to join before returning. A
    // value of 0 will wait infinitely. Return true if the thread is stopped.
    fun join(millis: Long = 0): Boolean {
        close()
        readThread?.let {
            it.join(millis)
            return it.isAlive
        }
        return true
    }

    // Returns true if the thread is still alive.
    fun isAlive(): Boolean {
        return readThread?.isAlive ?: false
    }
}